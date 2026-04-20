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
    private static final String PROJECT_WIDE_FILE = "project-wide";
    private static final int MAX_OUTPUT_CHARS = 200_000;
    private static final int DOCKER_CHECK_TIMEOUT_SECONDS = 10;

    private final ArrayList<Target> targets;
    private final boolean countSkippedAsFailure;
    private final boolean isolateWorkspaces;
    private final Set<String> workspaceExcludes;

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
        this.workspaceExcludes = loadWorkspaceExcludes(settings.optJSONArray("workspace_excludes"));
        this.targets = loadTargets(settings);

        if (this.targets.isEmpty()) {
            throw new IllegalArgumentException("portability_pass_rate requires at least one target in settings.targets");
        }
    }

    @Override
    public MetricResult evaluate(Path projectPath) throws Exception {
        if (projectPath == null) {
            throw new IllegalArgumentException("Project path cannot be null");
        }

        Path executionRoot = findExecutionRoot(projectPath);
        ArrayList<Finding> findings = new ArrayList<>();
        String hostOs = detectHostOs();
        DockerStatus dockerStatus = probeDocker();

        double totalWeight = 0.0;
        double failedWeight = 0.0;

        // Keep the score weighted, but only for targets that are actually meant to count.
        for (Target target : targets) {
            System.out.println(String.format(
                Locale.ROOT,
                "[PORTABILITY] Starting target '%s' (%s, support=%s, weight=%.2f)",
                target.name,
                target.mode,
                target.supportLevel,
                target.weight
            ));

            TargetOutcome outcome = executeTarget(target, executionRoot, hostOs, dockerStatus);
            boolean countedInScore = target.countsTowardScore()
                && (outcome.status != Status.SKIPPED || countSkippedAsFailure);

            if (countedInScore) {
                totalWeight += target.weight;
            }

            if (outcome.status == Status.COMPATIBILITY_FAILED) {
                failedWeight += countedInScore ? target.weight : 0.0;
            } else if (outcome.status == Status.SKIPPED && countedInScore) {
                failedWeight += target.weight;
            }

            findings.add(createFinding(target, outcome, executionRoot, countedInScore));
        }

        double riskScore = totalWeight == 0.0 ? 0.0 : clamp01(failedWeight / totalWeight);
        return new MetricResult(this, riskScore, findings, thresholds());
    }

    private TargetOutcome executeTarget(
        Target target,
        Path executionRoot,
        String hostOs,
        DockerStatus dockerStatus
    ) throws Exception {
        if (!target.oses.isEmpty() && !target.oses.contains(hostOs)) {
            return TargetOutcome.skipped("target not applicable for host OS");
        }

        if (target.mode == Mode.DOCKER && !dockerStatus.available) {
            return TargetOutcome.infrastructureFailed("docker-unavailable", dockerStatus.message, "");
        }

        Path workspace = executionRoot;
        Path temporaryWorkspace = null;

        try {
            // This keeps one target from leaving junk behind for the next one.
            if (target.isolateWorkspace) {
                temporaryWorkspace = copyWorkspace(executionRoot, target.name);
                workspace = temporaryWorkspace;
            }

            if (target.mode == Mode.DOCKER) {
                return runDockerTarget(target, workspace);
            }

            return runLocalTarget(target, workspace);
        } catch (IOException e) {
            return TargetOutcome.infrastructureFailed(
                "workspace-prepare-failed",
                "Failed to prepare isolated workspace: " + e.getMessage(),
                ""
            );
        } finally {
            deleteQuietly(temporaryWorkspace);
        }
    }

    private TargetOutcome runLocalTarget(Target target, Path workspace) throws Exception {
        CommandResult result = runCommand(normalizeLocalCommand(target.command), workspace, target.timeoutSeconds);
        return classify(result);
    }

    private TargetOutcome runDockerTarget(Target target, Path workspace) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("-v");
        command.add(dockerMount(workspace));
        command.add("-w");
        command.add("/workspace");
        command.add(target.dockerImage);
        command.addAll(target.command);

        CommandResult result = runCommand(command, null, target.timeoutSeconds);
        return classify(result);
    }

    private TargetOutcome classify(CommandResult result) {
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

    private List<String> normalizeLocalCommand(List<String> command) {
        if (command.isEmpty() || !"windows".equals(detectHostOs())) {
            return command;
        }

        // Windows can be a bit annoying with Maven, so just run it through cmd and move on.
        String executable = command.get(0).toLowerCase(Locale.ROOT);
        boolean looksLikeMaven =
            "mvn".equals(executable)
                || "mvn.cmd".equals(executable)
                || "mvn.bat".equals(executable)
                || "./mvnw".equals(executable)
                || "mvnw".equals(executable)
                || "mvnw.cmd".equals(executable)
                || "mvnw.bat".equals(executable);

        if (!looksLikeMaven) {
            return command;
        }

        ArrayList<String> wrapped = new ArrayList<>();
        wrapped.add("cmd");
        wrapped.add("/c");
        wrapped.addAll(command);
        return wrapped;
    }

    private CommandResult runCommand(List<String> command, Path workingDir, int timeoutSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return CommandResult.spawnFailed(
                "Failed to start command: " + String.join(" ", command) + " (" + e.getMessage() + ")"
            );
        }

        StringBuilder output = new StringBuilder();
        Thread reader = startReader(process, output);

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            reader.join(1000);
            return CommandResult.timedOut(output.toString());
        }

        reader.join(1000);
        return CommandResult.completed(process.exitValue(), output.toString());
    }

    private Thread startReader(Process process, StringBuilder output) {
        Thread reader = new Thread(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    // The output is useful for debugging, but there is no point letting it blow up forever.
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append('\n');
                    }
                }

                if (output.length() > MAX_OUTPUT_CHARS) {
                    output.setLength(MAX_OUTPUT_CHARS);
                    output.append("\n...output truncated...\n");
                }
            } catch (IOException e) {
                if (output.length() < MAX_OUTPUT_CHARS) {
                    output.append("\n[output-read-failed] ").append(e.getMessage()).append('\n');
                }
            }
        });

        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private DockerStatus probeDocker() {
        try {
            // If Docker is missing, that should show up as an infra issue, not as the whole metric exploding.
            CommandResult result = runCommand(
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
            if (version.isBlank()) {
                return DockerStatus.available("Docker daemon available");
            }
            return DockerStatus.available(version);
        } catch (Exception e) {
            return DockerStatus.unavailable("Docker check failed: " + e.getMessage());
        }
    }

    private ArrayList<Target> loadTargets(JSONObject settings) {
        JSONArray targetArray = settings.getJSONArray("targets");
        ArrayList<Target> loadedTargets = new ArrayList<>();

        for (int i = 0; i < targetArray.length(); i++) {
            JSONObject targetObject = targetArray.getJSONObject(i);

            String name = targetObject.getString("name");
            Mode mode = Mode.valueOf(targetObject.optString("mode", "LOCAL").trim().toUpperCase(Locale.ROOT));
            SupportLevel supportLevel = SupportLevel.valueOf(
                targetObject.optString("support_level", "SUPPORTED").trim().toUpperCase(Locale.ROOT)
            );

            ArrayList<String> oses = new ArrayList<>();
            JSONArray osArray = targetObject.optJSONArray("os");
            if (osArray != null) {
                for (int j = 0; j < osArray.length(); j++) {
                    oses.add(osArray.getString(j).trim().toLowerCase(Locale.ROOT));
                }
            }

            JSONArray commandArray = targetObject.getJSONArray("command");
            ArrayList<String> command = new ArrayList<>();
            for (int j = 0; j < commandArray.length(); j++) {
                command.add(commandArray.getString(j));
            }
            if (command.isEmpty()) {
                throw new IllegalArgumentException("Target '" + name + "' command must not be empty");
            }

            String dockerImage = targetObject.optString("docker_image", null);
            if (mode == Mode.DOCKER && (dockerImage == null || dockerImage.isBlank())) {
                throw new IllegalArgumentException("Target '" + name + "' mode=DOCKER requires docker_image");
            }

            double weight = targetObject.has("weight") ? targetObject.getDouble("weight") : 1.0;
            if (weight < 0.0) {
                throw new IllegalArgumentException("Target '" + name + "' weight must be >= 0");
            }

            boolean shouldIsolate = targetObject.has("isolate_workspace")
                ? targetObject.getBoolean("isolate_workspace")
                : isolateWorkspaces;

            Severity failSeverity = Severity.valueOf(
                targetObject.optString("fail_severity", "MAJOR").trim().toUpperCase(Locale.ROOT)
            );

            loadedTargets.add(new Target(
                name,
                mode,
                supportLevel,
                oses,
                dockerImage,
                command,
                targetObject.optInt("timeout_seconds", 600),
                weight,
                failSeverity,
                shouldIsolate
            ));
        }

        return loadedTargets;
    }

    private Set<String> loadWorkspaceExcludes(JSONArray excludes) {
        HashSet<String> values = new HashSet<>();
        values.add(".git");
        values.add("target");

        if (excludes == null) {
            return values;
        }

        for (int i = 0; i < excludes.length(); i++) {
            values.add(excludes.getString(i));
        }
        return values;
    }

    private Path findExecutionRoot(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        Path candidate = Files.isDirectory(normalized) ? normalized : normalized.getParent();

        // Walk upward until we hit the Maven root. If there is no pom, just use the closest real path.
        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }

        if (Files.isDirectory(normalized)) {
            return normalized;
        }
        return normalized.getParent() == null ? normalized : normalized.getParent();
    }

    private Path copyWorkspace(Path sourceRoot, String targetName) throws IOException {
        Path workspace = Files.createTempDirectory("portability-" + sanitize(targetName) + "-");

        // Copy the project into a throwaway workspace so tests can poke at files safely.
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

    private boolean shouldSkip(Path relativePath) {
        for (Path part : relativePath) {
            if (workspaceExcludes.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private void deleteQuietly(Path path) {
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
            // Temporary workspace cleanup is best-effort.
        }
    }

    private String detectHostOs() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("linux")) {
            return "linux";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "macos";
        }
        return "unknown";
    }

    private String dockerMount(Path workspace) {
        String absolutePath = workspace.toAbsolutePath().normalize().toString();

        if (absolutePath.length() >= 2 && absolutePath.charAt(1) == ':') {
            String drive = String.valueOf(Character.toLowerCase(absolutePath.charAt(0)));
            String rest = absolutePath.substring(2).replace('\\', '/');
            return drive + ":" + rest + ":/workspace";
        }

        return absolutePath + ":/workspace";
    }

    private Finding createFinding(Target target, TargetOutcome outcome, Path executionRoot, boolean countedInScore) {
        Severity severity;
        if (outcome.status == Status.PASSED || outcome.status == Status.SKIPPED) {
            severity = Severity.INFO;
        } else if (outcome.status == Status.INFRASTRUCTURE_FAILED) {
            severity = Severity.MAJOR;
        } else {
            severity = target.failSeverity;
        }

        // Infra failures are still worth reporting, they just should not pretend to affect the weighted risk.
        boolean showScoringFlag = outcome.status != Status.INFRASTRUCTURE_FAILED && countedInScore;
        String message = formatTargetMessage(target, outcome, showScoringFlag);

        if (outcome.status == Status.INFRASTRUCTURE_FAILED) {
            message = formatTargetMessage(target, outcome, false);
        }

        return new Finding(
            severity,
            message,
            PROJECT_WIDE_FILE,
            target.name,
            null
        );
    }

    private String formatTargetMessage(Target target, TargetOutcome outcome, boolean countedInScore) {
        String scoreNote = countedInScore
            ? String.format(Locale.ROOT, "counted in weighted risk (weight=%.2f)", target.weight)
            : String.format(
                Locale.ROOT,
                "excluded from weighted risk (support=%s, weight=%.2f)",
                target.supportLevel,
                target.weight
            );

        switch (outcome.status) {
            case PASSED:
                return String.format(Locale.ROOT, "Target '%s' PASSED. %s", target.name, scoreNote);
            case COMPATIBILITY_FAILED:
                return String.format(
                    Locale.ROOT,
                    "Target '%s' COMPATIBILITY FAILED (%s). %s %s",
                    target.name,
                    outcome.detail,
                    scoreNote,
                    truncate(outcome.output, 1200)
                );
            case INFRASTRUCTURE_FAILED:
                return String.format(
                    Locale.ROOT,
                    "Target '%s' INFRASTRUCTURE FAILED (%s). %s",
                    target.name,
                    outcome.message,
                    truncate(outcome.output, 1200)
                );
            case SKIPPED:
                return String.format(
                    Locale.ROOT,
                    "Target '%s' skipped: %s. %s",
                    target.name,
                    outcome.message,
                    scoreNote
                );
            default:
                throw new IllegalStateException("Unknown target status: " + outcome.status);
        }
    }

    private String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "\n...truncated...\n";
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private enum Mode {
        LOCAL,
        DOCKER
    }

    private enum SupportLevel {
        REQUIRED,
        SUPPORTED,
        EXPERIMENTAL
    }

    private enum Status {
        PASSED,
        COMPATIBILITY_FAILED,
        INFRASTRUCTURE_FAILED,
        SKIPPED
    }

    private static final class Target {
        private final String name;
        private final Mode mode;
        private final SupportLevel supportLevel;
        private final ArrayList<String> oses;
        private final String dockerImage;
        private final ArrayList<String> command;
        private final int timeoutSeconds;
        private final double weight;
        private final Severity failSeverity;
        private final boolean isolateWorkspace;

        private Target(
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

        private boolean countsTowardScore() {
            return weight > 0.0 && supportLevel != SupportLevel.EXPERIMENTAL;
        }
    }

    private static final class TargetOutcome {
        private final Status status;
        private final String detail;
        private final String message;
        private final String output;

        private TargetOutcome(Status status, String detail, String message, String output) {
            this.status = status;
            this.detail = detail;
            this.message = message;
            this.output = output;
        }

        private static TargetOutcome passed() {
            return new TargetOutcome(Status.PASSED, "", "", "");
        }

        private static TargetOutcome skipped(String reason) {
            return new TargetOutcome(Status.SKIPPED, "skipped", reason, "");
        }

        private static TargetOutcome compatibilityFailed(String detail, String message, String output) {
            return new TargetOutcome(Status.COMPATIBILITY_FAILED, detail, message, output);
        }

        private static TargetOutcome infrastructureFailed(String detail, String message, String output) {
            return new TargetOutcome(Status.INFRASTRUCTURE_FAILED, detail, message, output);
        }
    }

    private static final class DockerStatus {
        private final boolean available;
        private final String message;

        private DockerStatus(boolean available, String message) {
            this.available = available;
            this.message = message;
        }

        private static DockerStatus available(String message) {
            return new DockerStatus(true, message);
        }

        private static DockerStatus unavailable(String message) {
            return new DockerStatus(false, message);
        }
    }

    private static final class CommandResult {
        private final boolean started;
        private final boolean timedOut;
        private final int exitCode;
        private final String output;
        private final String message;

        private CommandResult(boolean started, boolean timedOut, int exitCode, String output, String message) {
            this.started = started;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
            this.message = message;
        }

        private static CommandResult spawnFailed(String message) {
            return new CommandResult(false, false, -1, "", message);
        }

        private static CommandResult timedOut(String output) {
            return new CommandResult(true, true, -1, output, "Timed out while running command");
        }

        private static CommandResult completed(int exitCode, String output) {
            return new CommandResult(true, false, exitCode, output, "");
        }
    }
}
