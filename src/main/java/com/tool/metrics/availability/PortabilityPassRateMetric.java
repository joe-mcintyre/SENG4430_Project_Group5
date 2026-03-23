package com.tool.metrics.availability;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class PortabilityPassRateMetric extends Metric {

    private static final int OUTPUT_LIMIT_CHARS = 200_000;
    private static final int DOCKER_CHECK_TIMEOUT_SECONDS = 10;

    private final ArrayList<Target> targets;
    private final boolean countSkippedAsFailure;
    private final boolean isolateWorkspaces;
    private final Set<String> workspaceExcludes;

    /**
     * Creates the portability metric using the configured targets and scoring settings.
     * @param thresholds The thresholds used to classify the metric score
     * @param settings The portability metric settings loaded from configuration
     */
    public PortabilityPassRateMetric(ArrayList<Threshold> thresholds, JSONObject settings) {
        super(
            thresholds,
            "Portability Pass Rate",
            "Measures portability as a weighted compatibility risk across configured JDK/OS targets."
        );
        if (settings == null) {
            throw new IllegalArgumentException("portability_pass_rate settings cannot be null");
        }

        this.countSkippedAsFailure = settings.optBoolean("count_skipped_as_failure", false);
        this.isolateWorkspaces = settings.optBoolean("isolate_workspaces", true);
        this.workspaceExcludes = parseWorkspaceExcludes(settings.optJSONArray("workspace_excludes"));
        this.targets = parseTargets(settings);

        if (this.targets.isEmpty()) {
            throw new IllegalArgumentException("portability_pass_rate requires at least one target in settings.targets");
        }
    }

    /**
     * Evaluates the project against each configured portability target.
     * @param projectPath The source path provided to the metric for evaluation
     * @return The final portability metric result and target findings
     * @throws Exception Thrown when target execution fails unexpectedly
     */
    @Override
    public MetricResult evaluate(Path projectPath) throws Exception {
        if (projectPath == null) {
            throw new IllegalArgumentException("Project path cannot be null");
        }

        Path executionRoot = resolveExecutionRoot(projectPath);
        ArrayList<Finding> findings = new ArrayList<>();

        String hostOs = detectOs();
        DockerStatus dockerStatus = checkDockerAvailability();

        int passed = 0;
        int compatibilityFailed = 0;
        int infrastructureFailed = 0;
        int skipped = 0;
        double totalWeightedTargets = 0.0;
        double weightedCompatibilityFailures = 0.0;

        for (Target target : targets) {
            System.out.println(String.format(
                Locale.ROOT,
                "[PORTABILITY] Starting target '%s' (%s, support=%s, weight=%.2f)",
                target.name,
                target.mode,
                target.supportLevel,
                target.weight
            ));
            TargetOutcome outcome = runTarget(target, executionRoot, hostOs, dockerStatus);
            boolean countedInScore = target.contributesToScore()
                && (outcome.status != Status.SKIPPED || countSkippedAsFailure);

            if (countedInScore) {
                totalWeightedTargets += target.weight;
            }

            switch (outcome.status) {
                case PASSED -> {
                    passed++;
                    findings.add(new Finding(
                        Severity.INFO,
                        formatTargetMessage(target, outcome, countedInScore),
                        executionRoot.toString(),
                        target.name,
                        null
                    ));
                }
                case COMPATIBILITY_FAILED -> {
                    compatibilityFailed++;
                    if (countedInScore) {
                        weightedCompatibilityFailures += target.weight;
                    }
                    findings.add(new Finding(
                        target.failSeverity,
                        formatTargetMessage(target, outcome, countedInScore),
                        executionRoot.toString(),
                        target.name,
                        null
                    ));
                }
                case INFRASTRUCTURE_FAILED -> {
                    infrastructureFailed++;
                    findings.add(new Finding(
                        Severity.MAJOR,
                        formatTargetMessage(target, outcome, false),
                        executionRoot.toString(),
                        target.name,
                        null
                    ));
                }
                case SKIPPED -> {
                    skipped++;
                    if (countedInScore) {
                        weightedCompatibilityFailures += target.weight;
                    }
                    findings.add(new Finding(
                        Severity.INFO,
                        formatTargetMessage(target, outcome, countedInScore),
                        executionRoot.toString(),
                        target.name,
                        null
                    ));
                }
            }
        }

        double riskScore = totalWeightedTargets == 0.0
            ? 0.0
            : clamp01(weightedCompatibilityFailures / totalWeightedTargets);

        return new MetricResult(this, riskScore, findings, thresholds());
    }

    /**
     * Runs a single portability target and classifies its outcome.
     * @param target The configured target to execute
     * @param executionRoot The resolved Maven project root
     * @param hostOs The detected host operating system
     * @param dockerStatus The current Docker availability status
     * @return The outcome produced by the target execution
     * @throws Exception Thrown when the target command fails unexpectedly
     */
    private TargetOutcome runTarget(Target target, Path executionRoot, String hostOs, DockerStatus dockerStatus) throws Exception {
        if (!target.oses.isEmpty() && !target.oses.contains(hostOs)) {
            return TargetOutcome.skipped("target not applicable for host OS");
        }

        if (target.mode == Mode.DOCKER && !dockerStatus.available) {
            return TargetOutcome.infrastructureFailed("docker-unavailable", dockerStatus.message, "");
        }

        Path workspace = executionRoot;
        boolean usingTemporaryWorkspace = target.shouldIsolateWorkspace();

        try {
            if (usingTemporaryWorkspace) {
                workspace = createWorkspaceCopy(executionRoot, target.name);
            }

            if (target.mode == Mode.DOCKER) {
                return runDockerTarget(target, workspace);
            }

            return runLocalTarget(target, workspace);
        } catch (IOException ioe) {
            return TargetOutcome.infrastructureFailed(
                "workspace-prepare-failed",
                "Failed to prepare isolated workspace: " + ioe.getMessage(),
                ""
            );
        } finally {
            if (usingTemporaryWorkspace) {
                deleteRecursively(workspace);
            }
        }
    }

    /**
     * Executes a portability target directly on the host machine.
     * @param target The local target configuration
     * @param workspace The workspace directory used for the command
     * @return The classified outcome of the local command
     * @throws Exception Thrown when the command execution fails unexpectedly
     */
    private TargetOutcome runLocalTarget(Target target, Path workspace) throws Exception {
        List<String> cmd = normalizeLocalCommand(new ArrayList<>(target.command));
        CommandResult result = execCommand(cmd, workspace, target.timeoutSeconds);
        return classifyCommandResult(result);
    }

    /**
     * Executes a portability target inside its configured Docker image.
     * @param target The Docker target configuration
     * @param workspace The isolated workspace mounted into the container
     * @return The classified outcome of the Docker command
     * @throws Exception Thrown when the container command fails unexpectedly
     */
    private TargetOutcome runDockerTarget(Target target, Path workspace) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-v");
        cmd.add(dockerMount(workspace));
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add(target.dockerImage);
        cmd.addAll(target.command);

        CommandResult result = execCommand(cmd, null, target.timeoutSeconds);
        return classifyCommandResult(result);
    }

    /**
     * Converts a low-level command result into a portability target outcome.
     * @param result The completed command result
     * @return The classified portability outcome for the command
     */
    private TargetOutcome classifyCommandResult(CommandResult result) {
        if (!result.started) {
            return TargetOutcome.infrastructureFailed("spawn-failed", result.message, result.output);
        }

        if (result.timedOut) {
            return TargetOutcome.compatibilityFailed("timeout", "Timed out while running target command", result.output);
        }

        if (result.exitCode == 0) {
            return TargetOutcome.passed();
        }

        return TargetOutcome.compatibilityFailed(
            "exit-code",
            "Exit code " + result.exitCode,
            result.output
        );
    }

    /**
     * Normalizes local commands so Maven invocations run correctly on Windows hosts.
     * @param cmd The original local command tokens
     * @return The normalized command ready for ProcessBuilder execution
     */
    private List<String> normalizeLocalCommand(List<String> cmd) {
        if (cmd.isEmpty()) {
            return cmd;
        }

        if (!"windows".equals(detectOs())) {
            return cmd;
        }

        String first = cmd.get(0).toLowerCase(Locale.ROOT);
        boolean isMavenLike =
            first.equals("mvn") || first.equals("mvn.cmd") || first.equals("mvn.bat")
                || first.equals("./mvnw") || first.equals("mvnw") || first.equals("mvnw.cmd")
                || first.equals("mvnw.bat");

        if (!isMavenLike) {
            return cmd;
        }

        ArrayList<String> wrapped = new ArrayList<>();
        wrapped.add("cmd");
        wrapped.add("/c");
        wrapped.addAll(cmd);
        return wrapped;
    }

    /**
     * Executes a command and captures its combined process output.
     * @param cmd The command tokens to execute
     * @param workingDir The working directory for the process, or null for default
     * @param timeoutSeconds The maximum execution time in seconds
     * @return The command execution result including status and output
     * @throws Exception Thrown when waiting on the process fails unexpectedly
     */
    private CommandResult execCommand(List<String> cmd, Path workingDir, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }

        final Process process;
        try {
            process = pb.start();
        } catch (IOException ioe) {
            return CommandResult.spawnFailed(
                "Failed to start command: " + String.join(" ", cmd) + " (" + ioe.getMessage() + ")"
            );
        }

        StringBuilder output = new StringBuilder();
        Thread outputReader = startOutputReader(process, output);

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            outputReader.join(1000);
            return CommandResult.timedOut(output.toString());
        }

        outputReader.join(1000);
        return CommandResult.completed(process.exitValue(), output.toString());
    }

    /**
     * Starts a background reader that drains process output into a shared buffer.
     * @param process The running process whose output will be consumed
     * @param output The output buffer used to collect process text
     * @return The background thread reading process output
     */
    private Thread startOutputReader(Process process, StringBuilder output) {
        Thread reader = new Thread(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (output.length() < OUTPUT_LIMIT_CHARS) {
                        output.append(line).append('\n');
                    }
                }
                if (output.length() > OUTPUT_LIMIT_CHARS) {
                    output.setLength(OUTPUT_LIMIT_CHARS);
                    output.append("\n...output truncated...\n");
                }
            } catch (IOException ioe) {
                if (output.length() < OUTPUT_LIMIT_CHARS) {
                    output.append("\n[output-read-failed] ").append(ioe.getMessage()).append('\n');
                }
            }
        });
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    /**
     * Checks whether Docker is available and the daemon can accept commands.
     * @return The detected Docker availability status and diagnostic message
     */
    private DockerStatus checkDockerAvailability() {
        try {
            CommandResult result = execCommand(
                List.of("docker", "info", "--format", "{{.ServerVersion}}"),
                null,
                DOCKER_CHECK_TIMEOUT_SECONDS
            );

            if (!result.started) {
                return DockerStatus.unavailable(result.message);
            }
            if (result.timedOut) {
                return DockerStatus.unavailable("Docker daemon check timed out");
            }
            if (result.exitCode != 0) {
                return DockerStatus.unavailable("Docker daemon check failed: " + truncate(result.output, 400));
            }

            String version = truncate(result.output.trim(), 120);
            return DockerStatus.available(version.isBlank() ? "Docker daemon available" : version);
        } catch (Exception e) {
            return DockerStatus.unavailable("Docker check failed: " + e.getMessage());
        }
    }

    /**
     * Parses the configured portability targets from the metric settings.
     * @param settings The portability metric settings object
     * @return The configured list of portability targets
     */
    private ArrayList<Target> parseTargets(JSONObject settings) {
        JSONArray arr = settings.getJSONArray("targets");
        ArrayList<Target> result = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);

            String name = obj.getString("name");
            Mode mode = Mode.valueOf(obj.optString("mode", "LOCAL").trim().toUpperCase(Locale.ROOT));
            SupportLevel supportLevel = SupportLevel.valueOf(
                obj.optString("support_level", "SUPPORTED").trim().toUpperCase(Locale.ROOT)
            );

            ArrayList<String> oses = new ArrayList<>();
            if (obj.has("os")) {
                JSONArray osArr = obj.getJSONArray("os");
                for (int j = 0; j < osArr.length(); j++) {
                    oses.add(osArr.getString(j).trim().toLowerCase(Locale.ROOT));
                }
            }

            ArrayList<String> command = new ArrayList<>();
            JSONArray cmdArr = obj.getJSONArray("command");
            for (int j = 0; j < cmdArr.length(); j++) {
                command.add(cmdArr.getString(j));
            }
            if (command.isEmpty()) {
                throw new IllegalArgumentException("Target '" + name + "' command must not be empty");
            }

            String dockerImage = obj.optString("docker_image", null);
            if (mode == Mode.DOCKER && (dockerImage == null || dockerImage.isBlank())) {
                throw new IllegalArgumentException("Target '" + name + "' mode=DOCKER requires docker_image");
            }

            int timeoutSeconds = obj.optInt("timeout_seconds", 600);
            double weight = obj.has("weight") ? obj.getDouble("weight") : 1.0;
            if (weight < 0.0) {
                throw new IllegalArgumentException("Target '" + name + "' weight must be >= 0");
            }

            Severity failSeverity = Severity.valueOf(
                obj.optString("fail_severity", "MAJOR").trim().toUpperCase(Locale.ROOT)
            );

            boolean targetIsolation = obj.has("isolate_workspace")
                ? obj.getBoolean("isolate_workspace")
                : isolateWorkspaces;

            result.add(new Target(
                name,
                mode,
                supportLevel,
                oses,
                dockerImage,
                command,
                timeoutSeconds,
                weight,
                failSeverity,
                targetIsolation
            ));
        }

        return result;
    }

    /**
     * Parses the workspace copy exclusion list used during isolated target runs.
     * @param excludes The optional JSON array of directory or file names to exclude
     * @return The final set of excluded path parts
     */
    private Set<String> parseWorkspaceExcludes(JSONArray excludes) {
        HashSet<String> result = new HashSet<>();
        result.add(".git");
        result.add("target");

        if (excludes == null) {
            return result;
        }

        for (int i = 0; i < excludes.length(); i++) {
            result.add(excludes.getString(i));
        }

        return result;
    }

    /**
     * Resolves the Maven execution root by walking upward until a pom.xml is found.
     * @param projectPath The project path originally provided to the metric
     * @return The resolved execution root used for portability checks
     */
    private Path resolveExecutionRoot(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        Path candidate = Files.isDirectory(normalized) ? normalized : normalized.getParent();

        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }

        return Files.isDirectory(normalized) ? normalized : normalized.getParent();
    }

    /**
     * Creates an isolated workspace copy for a single target execution.
     * @param sourceRoot The source project root to copy
     * @param targetName The target name used when creating the temporary directory
     * @return The path to the isolated temporary workspace
     * @throws IOException Thrown when the workspace copy cannot be created
     */
    private Path createWorkspaceCopy(Path sourceRoot, String targetName) throws IOException {
        Path workspace = Files.createTempDirectory("portability-" + sanitize(targetName) + "-");

        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(dir);
                if (!relative.toString().isEmpty() && shouldSkip(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                Files.createDirectories(workspace.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(file);
                if (!shouldSkip(relative)) {
                    Files.copy(file, workspace.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return workspace;
    }

    /**
     * Determines whether a relative path should be excluded from a workspace copy.
     * @param relativePath The relative path being considered during copying
     * @return True if the path should be skipped, otherwise false
     */
    private boolean shouldSkip(Path relativePath) {
        for (Path part : relativePath) {
            if (workspaceExcludes.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes a temporary workspace and its contents using best-effort cleanup.
     * @param path The root path to delete recursively
     */
    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best effort cleanup for temporary workspaces.
        }
    }

    /**
     * Detects the current host operating system using normalized metric labels.
     * @return The normalized host operating system name
     */
    private String detectOs() {
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        return "unknown";
    }

    /**
     * Builds the Docker volume mount string for the given workspace path.
     * @param projectPath The workspace path that will be mounted into Docker
     * @return The Docker mount argument using /workspace as the container path
     */
    private String dockerMount(Path projectPath) {
        String abs = projectPath.toAbsolutePath().normalize().toString();

        if (abs.length() >= 2 && abs.charAt(1) == ':') {
            String drive = String.valueOf(Character.toLowerCase(abs.charAt(0)));
            String rest = abs.substring(2).replace('\\', '/');
            return drive + ":" + rest + ":/workspace";
        }

        return abs + ":/workspace";
    }

    /**
     * Formats the human-readable report message for a target outcome.
     * @param target The target that produced the outcome
     * @param outcome The result of the target execution
     * @param countedInScore Whether the target was included in weighted scoring
     * @return The formatted report message for the target
     */
    private String formatTargetMessage(Target target, TargetOutcome outcome, boolean countedInScore) {
        String scoreNote = countedInScore
            ? String.format(Locale.ROOT, "counted in weighted risk (weight=%.2f)", target.weight)
            : String.format(Locale.ROOT, "excluded from weighted risk (support=%s, weight=%.2f)", target.supportLevel, target.weight);

        return switch (outcome.status) {
            case PASSED -> String.format(
                Locale.ROOT,
                "Target '%s' PASSED. %s",
                target.name,
                scoreNote
            );
            case COMPATIBILITY_FAILED -> String.format(
                Locale.ROOT,
                "Target '%s' COMPATIBILITY FAILED (%s). %s %s",
                target.name,
                outcome.detail,
                scoreNote,
                truncate(outcome.output, 1200)
            );
            case INFRASTRUCTURE_FAILED -> String.format(
                Locale.ROOT,
                "Target '%s' INFRASTRUCTURE FAILED (%s). %s",
                target.name,
                outcome.message,
                truncate(outcome.output, 1200)
            );
            case SKIPPED -> String.format(
                Locale.ROOT,
                "Target '%s' skipped: %s. %s",
                target.name,
                outcome.message,
                scoreNote
            );
        };
    }

    /**
     * Sanitizes a value so it is safe to use in temporary workspace names.
     * @param value The raw value to sanitize
     * @return The sanitized lowercase identifier
     */
    private String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    /**
     * Truncates long output strings so report messages remain manageable.
     * @param s The source string to truncate
     * @param maxChars The maximum number of characters to keep
     * @return The original or truncated string value
     */
    private String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n...truncated...\n";
    }

    /**
     * Clamps a numeric value to the inclusive range between 0.0 and 1.0.
     * @param v The value to clamp
     * @return The clamped value
     */
    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private enum Mode { LOCAL, DOCKER }

    private enum SupportLevel { REQUIRED, SUPPORTED, EXPERIMENTAL }

    private enum Status { PASSED, COMPATIBILITY_FAILED, INFRASTRUCTURE_FAILED, SKIPPED }

    private static final class Target {
        final String name;
        final Mode mode;
        final SupportLevel supportLevel;
        final ArrayList<String> oses;
        final String dockerImage;
        final ArrayList<String> command;
        final int timeoutSeconds;
        final double weight;
        final Severity failSeverity;
        final boolean isolateWorkspace;

        /**
         * Creates a configured portability target definition.
         * @param name The display name of the target
         * @param mode The execution mode used for the target
         * @param supportLevel The support level assigned to the target
         * @param oses The supported host operating systems for the target
         * @param dockerImage The Docker image used when mode is DOCKER
         * @param command The command executed for the target
         * @param timeoutSeconds The maximum execution time in seconds
         * @param weight The weighted score contribution of the target
         * @param failSeverity The severity used when the target compatibility fails
         * @param isolateWorkspace Whether the target should run in an isolated workspace
         */
        Target(
            String name,
            Mode mode,
            SupportLevel supportLevel,
            ArrayList<String> oses,
            String dockerImage,
            ArrayList<String> command,
            int timeoutSeconds,
            double weight,
            Severity failSeverity,
            boolean isolateWorkspace
        ) {
            this.name = name;
            this.mode = mode;
            this.supportLevel = supportLevel;
            this.oses = oses;
            this.dockerImage = dockerImage;
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
            this.weight = weight;
            this.failSeverity = failSeverity;
            this.isolateWorkspace = isolateWorkspace;
        }

        /**
         * Determines whether this target contributes to the weighted score.
         * @return True when the target is weighted and not experimental
         */
        boolean contributesToScore() {
            return weight > 0.0 && supportLevel != SupportLevel.EXPERIMENTAL;
        }

        /**
         * Indicates whether this target should run in an isolated workspace copy.
         * @return True when workspace isolation is enabled for this target
         */
        boolean shouldIsolateWorkspace() {
            return isolateWorkspace;
        }
    }

    private static final class TargetOutcome {
        final Status status;
        final String detail;
        final String message;
        final String output;

        /**
         * Creates a target outcome for a portability execution result.
         * @param status The high-level target status
         * @param detail The short detail code for the outcome
         * @param message The descriptive message for the outcome
         * @param output The command output captured for the outcome
         */
        private TargetOutcome(Status status, String detail, String message, String output) {
            this.status = status;
            this.detail = detail;
            this.message = message;
            this.output = output;
        }

        /**
         * Creates a successful target outcome.
         * @return A passed target outcome
         */
        static TargetOutcome passed() {
            return new TargetOutcome(Status.PASSED, "", "", "");
        }

        /**
         * Creates a skipped target outcome.
         * @param reason The reason the target was skipped
         * @return A skipped target outcome
         */
        static TargetOutcome skipped(String reason) {
            return new TargetOutcome(Status.SKIPPED, "skipped", reason, "");
        }

        /**
         * Creates a compatibility failure outcome for a target.
         * @param detail The short detail code for the failure
         * @param message The descriptive failure message
         * @param output The captured command output
         * @return A compatibility failure target outcome
         */
        static TargetOutcome compatibilityFailed(String detail, String message, String output) {
            return new TargetOutcome(Status.COMPATIBILITY_FAILED, detail, message, output);
        }

        /**
         * Creates an infrastructure failure outcome for a target.
         * @param detail The short detail code for the failure
         * @param message The descriptive failure message
         * @param output The captured command output
         * @return An infrastructure failure target outcome
         */
        static TargetOutcome infrastructureFailed(String detail, String message, String output) {
            return new TargetOutcome(Status.INFRASTRUCTURE_FAILED, detail, message, output);
        }
    }

    private static final class DockerStatus {
        final boolean available;
        final String message;

        /**
         * Creates the Docker availability state used by portability checks.
         * @param available Whether Docker is available for target execution
         * @param message The diagnostic message describing Docker status
         */
        private DockerStatus(boolean available, String message) {
            this.available = available;
            this.message = message;
        }

        /**
         * Creates an available Docker status.
         * @param message The descriptive Docker status message
         * @return An available Docker status
         */
        static DockerStatus available(String message) {
            return new DockerStatus(true, message);
        }

        /**
         * Creates an unavailable Docker status.
         * @param message The descriptive Docker status message
         * @return An unavailable Docker status
         */
        static DockerStatus unavailable(String message) {
            return new DockerStatus(false, message);
        }
    }

    private static final class CommandResult {
        final boolean started;
        final boolean timedOut;
        final int exitCode;
        final String output;
        final String message;

        /**
         * Creates a low-level command execution result.
         * @param started Whether the command process started successfully
         * @param timedOut Whether the command exceeded its timeout
         * @param exitCode The process exit code when available
         * @param output The captured command output
         * @param message The descriptive status message for the result
         */
        private CommandResult(boolean started, boolean timedOut, int exitCode, String output, String message) {
            this.started = started;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
            this.message = message;
        }

        /**
         * Creates a command result representing a process spawn failure.
         * @param message The descriptive spawn failure message
         * @return A failed command result
         */
        static CommandResult spawnFailed(String message) {
            return new CommandResult(false, false, -1, "", message);
        }

        /**
         * Creates a command result representing a timeout.
         * @param output The command output captured before timing out
         * @return A timed out command result
         */
        static CommandResult timedOut(String output) {
            return new CommandResult(true, true, -1, output, "Timed out while running command");
        }

        /**
         * Creates a command result representing a completed process.
         * @param exitCode The final process exit code
         * @param output The captured command output
         * @return A completed command result
         */
        static CommandResult completed(int exitCode, String output) {
            return new CommandResult(true, false, exitCode, output, "");
        }
    }
}
