package com.tool.metrics.availability;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class PortabilityPassRateMetric extends Metric {

    private final ArrayList<Target> targets;
    private final boolean countSkippedAsFailure;

    public PortabilityPassRateMetric(ArrayList<Threshold> thresholds, JSONObject settings) {
        super(
            thresholds,
            "Portability Pass Rate",
            "Measures availability/portability by running build/test checks across configured OS/JDK targets (host + Docker). Score is failure rate (lower is better)."
        );

        if (settings == null) {
            throw new IllegalArgumentException("portability_pass_rate settings cannot be null");
        }

        this.countSkippedAsFailure = settings.optBoolean("count_skipped_as_failure", true);
        this.targets = parseTargets(settings);
        if (this.targets.isEmpty()) {
            throw new IllegalArgumentException("portability_pass_rate requires at least one target in settings.targets");
        }
    }

    @Override
    public MetricResult evaluate(Path projectPath) throws Exception {
        if (projectPath == null) {
            throw new IllegalArgumentException("Project path cannot be null");
        }

        System.out.println("[DEBUG] [PortabilityPassRateMetric] Starting portability evaluation");
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Project path: " + projectPath);
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Number of targets to evaluate: " + targets.size());

        ArrayList<Finding> findings = new ArrayList<>();

        String hostOs = detectOs();
        boolean dockerAvailable = isDockerAvailable();
        
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Host OS: " + hostOs);
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Docker available: " + dockerAvailable);

        int total = 0;
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        for (Target t : targets) {
            System.out.println("[DEBUG] [PortabilityPassRateMetric] Evaluating target: " + t.name);
            TargetOutcome outcome = runTarget(t, projectPath, hostOs, dockerAvailable);
            System.out.println("[DEBUG] [PortabilityPassRateMetric] Target '" + t.name + "' status: " + outcome.status);

            if (outcome.status == Status.SKIPPED) {
                skipped++;
                System.out.println("[DEBUG] [PortabilityPassRateMetric] Target '" + t.name + "' skipped: " + outcome.message);
                if (countSkippedAsFailure) {
                    total++;
                    failed++;
                    System.out.println("[DEBUG] [PortabilityPassRateMetric] Counting skipped target as failure");
                }
                findings.add(new Finding(
                    Severity.INFO,
                    String.format("Target '%s' skipped: %s", t.name, outcome.message),
                    projectPath.toString(),
                    t.name,
                    null
                ));
                continue;
            }

            total++;

            if (outcome.status == Status.PASSED) {
                passed++;
                System.out.println("[DEBUG] [PortabilityPassRateMetric] Target '" + t.name + "' PASSED");
                findings.add(new Finding(
                    Severity.INFO,
                    String.format("Target '%s' PASSED successfully", t.name),
                    projectPath.toString(),
                    t.name,
                    null
                ));
                continue;
            }

            failed++;
            System.out.println("[DEBUG] [PortabilityPassRateMetric] Target '" + t.name + "' FAILED: " + outcome.detail);
            findings.add(new Finding(
                t.failSeverity,
                String.format("Target '%s' FAILED (%s). %s", t.name, outcome.detail, truncate(outcome.output, 1200)),
                projectPath.toString(),
                t.name,
                null
            ));
        }

        double passRate = total == 0 ? 0.0 : ((double) passed / (double) total);
        double failureRate = clamp01(1.0 - passRate);
        
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Results - Passed: " + passed + ", Failed: " + failed + ", Skipped: " + skipped + ", Total: " + total);
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Pass Rate: " + String.format("%.3f", passRate));
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Failure Rate (Score): " + String.format("%.3f", failureRate));

        Severity summaryExpectation = failed > 0 ? Severity.MAJOR : (skipped > 0 ? Severity.INFO : Severity.INFO);
        findings.add(0, new Finding(
            summaryExpectation,
            String.format(
                "Portability summary: hostOS=%s, dockerAvailable=%s, passed=%d, failed=%d, skipped=%d, totalCounted=%d, passRate=%.3f, failureRate(score)=%.3f",
                hostOs,
                dockerAvailable,
                passed,
                failed,
                skipped,
                total,
                passRate,
                failureRate
            ),
            projectPath.toString(),
            "project-wide",
            null
        ));
        
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Total findings: " + findings.size());
        System.out.println("[DEBUG] [PortabilityPassRateMetric] Portability evaluation complete");

        return new MetricResult(this, failureRate, findings, thresholds());
    }

    private List<String> normalizeLocalCommand(List<String> cmd) {
        System.out.println("[DEBUG] [normalizeLocalCommand] Original command: " + cmd);
        
        if (cmd.isEmpty()) {
            System.out.println("[DEBUG] [normalizeLocalCommand] Command is empty, returning as-is");
            return cmd;
        }

        if (!"windows".equals(detectOs())) {
            System.out.println("[DEBUG] [normalizeLocalCommand] Not Windows OS, returning as-is");
            return cmd;
        }

        String first = cmd.get(0).toLowerCase(Locale.ROOT);
        System.out.println("[DEBUG] [normalizeLocalCommand] First command element (lowercase): " + first);

        // Common cases that are batch scripts / need shell resolution on Windows.
        boolean isMavenLike =
                first.equals("mvn") || first.equals("mvn.cmd") || first.equals("mvn.bat") ||
                first.equals("./mvnw") || first.equals("mvnw") || first.equals("mvnw.cmd") || first.equals("mvnw.bat");

        System.out.println("[DEBUG] [normalizeLocalCommand] Is Maven-like: " + isMavenLike);

        if (!isMavenLike) {
            System.out.println("[DEBUG] [normalizeLocalCommand] Not Maven-like, returning as-is");
            return cmd;
        }

        ArrayList<String> wrapped = new ArrayList<>();
        wrapped.add("cmd");
        wrapped.add("/c");
        wrapped.addAll(cmd);
        System.out.println("[DEBUG] [normalizeLocalCommand] Wrapped with cmd /c: " + wrapped);
        return wrapped;
    }

    // -------------------------
    // Target execution
    // -------------------------

    private TargetOutcome runTarget(Target t, Path projectPath, String hostOs, boolean dockerAvailable) throws Exception {
        System.out.println("[DEBUG] [runTarget] Checking target '" + t.name + "' applicability");
        System.out.println("[DEBUG] [runTarget] Target oses: " + t.oses + ", Mode: " + t.mode);
        
        if (!t.oses.isEmpty() && !t.oses.contains(hostOs)) {
            System.out.println("[DEBUG] [runTarget] Target not applicable for host OS: " + hostOs);
            return TargetOutcome.skipped("target not applicable for host OS");
        }

        if (t.mode == Mode.DOCKER) {
            System.out.println("[DEBUG] [runTarget] Docker mode - checking availability");
            if (!dockerAvailable) {
                System.out.println("[DEBUG] [runTarget] Docker not available");
                return countSkippedAsFailure
                    ? TargetOutcome.failed("docker-not-available", "Docker is not available on this machine", "")
                    : TargetOutcome.skipped("docker not available");
            }
            System.out.println("[DEBUG] [runTarget] Running Docker target");
            return runDockerTarget(t, projectPath);
        }

        System.out.println("[DEBUG] [runTarget] Running local target");
        return runLocalTarget(t, projectPath);
    }

    private TargetOutcome runLocalTarget(Target t, Path projectPath) throws Exception {
        System.out.println("[DEBUG] [runLocalTarget] Target: " + t.name);
        System.out.println("[DEBUG] [runLocalTarget] Command: " + t.command);
        List<String> cmd = normalizeLocalCommand(new ArrayList<>(t.command));
        System.out.println("[DEBUG] [runLocalTarget] Normalized command: " + cmd);
        return execCommand(cmd, projectPath, t.timeoutSeconds);
    }

    private TargetOutcome runDockerTarget(Target t, Path projectPath) throws Exception {
        System.out.println("[DEBUG] [runDockerTarget] Target: " + t.name);
        System.out.println("[DEBUG] [runDockerTarget] Docker image: " + t.dockerImage);
        System.out.println("[DEBUG] [runDockerTarget] Command: " + t.command);
        
        String mount = dockerMount(projectPath);
        System.out.println("[DEBUG] [runDockerTarget] Docker mount: " + mount);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-v");
        cmd.add(mount);
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add(t.dockerImage);

        // command executed inside container
        cmd.addAll(t.command);
        
        System.out.println("[DEBUG] [runDockerTarget] Full Docker command: " + cmd);

        return execCommand(cmd, null, t.timeoutSeconds);
    }

   
    private TargetOutcome execCommand(List<String> cmd, Path workingDir, int timeoutSeconds) throws Exception {
        System.out.println("[DEBUG] [execCommand] Executing command: " + String.join(" ", cmd));
        System.out.println("[DEBUG] [execCommand] Working directory: " + (workingDir == null ? "(null - inherited)" : workingDir));
        System.out.println("[DEBUG] [execCommand] Timeout: " + timeoutSeconds + "s");
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }

        final Process p;
        try {
            System.out.println("[DEBUG] [execCommand] Starting process...");
            p = pb.start();
            System.out.println("[DEBUG] [execCommand] Process started successfully");
        } catch (java.io.IOException ioe) {
            // This is the exact case you're hitting: CreateProcess error=2, etc.
            System.out.println("[DEBUG] [execCommand] Failed to start process: " + ioe.getMessage());
            return TargetOutcome.failed(
                "spawn-failed",
                "Failed to start command: " + String.join(" ", cmd) + " (" + ioe.getMessage() + ")",
                ""
            );
        }

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = r.readLine()) != null) {
                lineCount++;
                out.append(line).append('\n');
                if (out.length() > 200_000) {
                    System.out.println("[DEBUG] [execCommand] Output truncated - exceeded 200KB");
                    out.append("\n...output truncated...\n");
                    break;
                }
            }
            System.out.println("[DEBUG] [execCommand] Read " + lineCount + " lines of output");
        }

        boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            System.out.println("[DEBUG] [execCommand] Process timed out after " + timeoutSeconds + "s");
            p.destroyForcibly();
            return TargetOutcome.failed("timeout", "Timed out after " + timeoutSeconds + "s", out.toString());
        }

        int code = p.exitValue();
        System.out.println("[DEBUG] [execCommand] Process exited with code: " + code);
        if (code == 0) {
            System.out.println("[DEBUG] [execCommand] Command succeeded");
            return TargetOutcome.passed();
        }

        System.out.println("[DEBUG] [execCommand] Command failed with exit code " + code);
        return TargetOutcome.failed("exit-code", "Exit code " + code, out.toString());
    }

    // -------------------------
    // Settings parsing
    // -------------------------

    private ArrayList<Target> parseTargets(JSONObject settings) {
        System.out.println("[DEBUG] [parseTargets] Parsing portability targets from settings");
        JSONArray arr = settings.getJSONArray("targets");
        System.out.println("[DEBUG] [parseTargets] Found " + arr.length() + " targets");
        ArrayList<Target> result = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);

            String name = obj.getString("name");
            Mode mode = Mode.valueOf(obj.optString("mode", "LOCAL").trim().toUpperCase(Locale.ROOT));

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

            Severity failSeverity = Severity.valueOf(
                obj.optString("fail_severity", "MAJOR").trim().toUpperCase(Locale.ROOT)
            );

            System.out.println("[DEBUG] [parseTargets] Target " + (i+1) + ": name=" + name + ", mode=" + mode + 
                             ", os=" + (oses.isEmpty() ? "any" : oses) + ", command=" + command + 
                             ", timeout=" + timeoutSeconds + "s, failSeverity=" + failSeverity);

            result.add(new Target(name, mode, oses, dockerImage, command, timeoutSeconds, failSeverity));
        }

        return result;
    }

    // -------------------------
    // Helpers
    // -------------------------

    private boolean isDockerAvailable() {
        System.out.println("[DEBUG] [isDockerAvailable] Checking Docker availability...");
        try {
            Process p = new ProcessBuilder("docker", "--version")
                .redirectErrorStream(true)
                .start();
            boolean available = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            System.out.println("[DEBUG] [isDockerAvailable] Docker available: " + available);
            if (available) {
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = br.readLine();
                if (line != null) {
                    System.out.println("[DEBUG] [isDockerAvailable] Docker version: " + line);
                }
            }
            return available;
        } catch (Exception e) {
            System.out.println("[DEBUG] [isDockerAvailable] Docker check failed: " + e.getMessage());
            return false;
        }
    }

    private String detectOs() {
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        return "unknown";
    }

    private String dockerMount(Path projectPath) {
        // Docker Desktop on Windows generally accepts c:/path style.
        String abs = projectPath.toAbsolutePath().normalize().toString();

        if (abs.length() >= 2 && abs.charAt(1) == ':') {
            // Windows drive path
            String drive = String.valueOf(Character.toLowerCase(abs.charAt(0)));
            String rest = abs.substring(2).replace('\\', '/');
            String win = drive + ":" + rest; // c:/...
            return win + ":/workspace";
        }

        // Linux/mac
        return abs + ":/workspace";
    }

    private String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n...truncated...\n";
    }

    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    // -------------------------
    // Types
    // -------------------------

    private enum Mode { LOCAL, DOCKER }
    private enum Status { PASSED, FAILED, SKIPPED }

    private static final class Target {
        final String name;
        final Mode mode;
        final ArrayList<String> oses;
        final String dockerImage; // only for docker
        final ArrayList<String> command;
        final int timeoutSeconds;
        final Severity failSeverity;

        Target(
            String name,
            Mode mode,
            ArrayList<String> oses,
            String dockerImage,
            ArrayList<String> command,
            int timeoutSeconds,
            Severity failSeverity
        ) {
            this.name = name;
            this.mode = mode;
            this.oses = oses;
            this.dockerImage = dockerImage;
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
            this.failSeverity = failSeverity;
        }
    }

    private static final class TargetOutcome {
        final Status status;
        final String detail;
        final String message;
        final String output;

        private TargetOutcome(Status status, String detail, String message, String output) {
            this.status = status;
            this.detail = detail;
            this.message = message;
            this.output = output;
        }

        static TargetOutcome passed() {
            return new TargetOutcome(Status.PASSED, "", "", "");
        }

        static TargetOutcome skipped(String reason) {
            return new TargetOutcome(Status.SKIPPED, "skipped", reason, "");
        }

        static TargetOutcome failed(String detail, String message, String output) {
            return new TargetOutcome(Status.FAILED, detail, message, output);
        }
    }
}