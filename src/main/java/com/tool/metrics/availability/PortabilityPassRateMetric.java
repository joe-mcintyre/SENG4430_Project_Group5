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

        ArrayList<Finding> findings = new ArrayList<>();

        String hostOs = detectOs();
        boolean dockerAvailable = isDockerAvailable();

        int total = 0;
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        for (Target t : targets) {
            TargetOutcome outcome = runTarget(t, projectPath, hostOs, dockerAvailable);

            if (outcome.status == Status.SKIPPED) {
                skipped++;
                if (countSkippedAsFailure) {
                    total++;
                    failed++;
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
                continue;
            }

            failed++;
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

        findings.add(0, new Finding(
            Severity.INFO,
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

        return new MetricResult(this, failureRate, findings, thresholds());
    }

    private List<String> normalizeLocalCommand(List<String> cmd) {
        if (cmd.isEmpty()) return cmd;

        if (!"windows".equals(detectOs())) {
            return cmd;
        }

        String first = cmd.get(0).toLowerCase(Locale.ROOT);

        // Common cases that are batch scripts / need shell resolution on Windows.
        boolean isMavenLike =
                first.equals("mvn") || first.equals("mvn.cmd") || first.equals("mvn.bat") ||
                first.equals("./mvnw") || first.equals("mvnw") || first.equals("mvnw.cmd") || first.equals("mvnw.bat");

        if (!isMavenLike) {
            return cmd;
        }

        ArrayList<String> wrapped = new ArrayList<>();
        wrapped.add("cmd");
        wrapped.add("/c");
        wrapped.addAll(cmd);
        return wrapped;
    }

    // -------------------------
    // Target execution
    // -------------------------

    private TargetOutcome runTarget(Target t, Path projectPath, String hostOs, boolean dockerAvailable) throws Exception {
        if (!t.oses.isEmpty() && !t.oses.contains(hostOs)) {
            return TargetOutcome.skipped("target not applicable for host OS");
        }

        if (t.mode == Mode.DOCKER) {
            if (!dockerAvailable) {
                return countSkippedAsFailure
                    ? TargetOutcome.failed("docker-not-available", "Docker is not available on this machine", "")
                    : TargetOutcome.skipped("docker not available");
            }
            return runDockerTarget(t, projectPath);
        }

        return runLocalTarget(t, projectPath);
    }

    private TargetOutcome runLocalTarget(Target t, Path projectPath) throws Exception {
        List<String> cmd = normalizeLocalCommand(new ArrayList<>(t.command));
        return execCommand(cmd, projectPath, t.timeoutSeconds);
    }

    private TargetOutcome runDockerTarget(Target t, Path projectPath) throws Exception {
        String mount = dockerMount(projectPath);

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

        return execCommand(cmd, null, t.timeoutSeconds);
    }

   
    private TargetOutcome execCommand(List<String> cmd, Path workingDir, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }

        final Process p;
        try {
            p = pb.start();
        } catch (java.io.IOException ioe) {
            // This is the exact case you're hitting: CreateProcess error=2, etc.
            return TargetOutcome.failed(
                "spawn-failed",
                "Failed to start command: " + String.join(" ", cmd) + " (" + ioe.getMessage() + ")",
                ""
            );
        }

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
                if (out.length() > 200_000) {
                    out.append("\n...output truncated...\n");
                    break;
                }
            }
        }

        boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            return TargetOutcome.failed("timeout", "Timed out after " + timeoutSeconds + "s", out.toString());
        }

        int code = p.exitValue();
        if (code == 0) {
            return TargetOutcome.passed();
        }

        return TargetOutcome.failed("exit-code", "Exit code " + code, out.toString());
    }

    // -------------------------
    // Settings parsing
    // -------------------------

    private ArrayList<Target> parseTargets(JSONObject settings) {
        JSONArray arr = settings.getJSONArray("targets");
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

            result.add(new Target(name, mode, oses, dockerImage, command, timeoutSeconds, failSeverity));
        }

        return result;
    }

    // -------------------------
    // Helpers
    // -------------------------

    private boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "--version")
                .redirectErrorStream(true)
                .start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
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