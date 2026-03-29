package metrics.availability.portability_tests;

import java.nio.file.Files;
import java.nio.file.Path;

// Tiny helper program for the portability tests. It does just enough so the metric can launch something real.
public final class PortabilityTargetProbe {
    private PortabilityTargetProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: <action> [args...]");
        }

        switch (args[0]) {
            case "write" -> {
                requireArgs(args, 2);
                Files.writeString(Path.of(args[1]), "marker");
            }
            case "assert-missing" -> {
                requireArgs(args, 2);
                Path target = Path.of(args[1]);
                if (Files.exists(target)) {
                    System.err.println("Expected file to be missing: " + target);
                    System.exit(1);
                }
            }
            case "assert-exists" -> {
                requireArgs(args, 2);
                Path target = Path.of(args[1]);
                if (!Files.exists(target)) {
                    System.err.println("Expected file to exist: " + target);
                    System.exit(1);
                }
            }
            case "sleep" -> {
                requireArgs(args, 2);
                // This is only here so the timeout path can be tested on purpose.
                Thread.sleep(Long.parseLong(args[1]));
            }
            default -> throw new IllegalArgumentException("Unknown action: " + args[0]);
        }
    }

    private static void requireArgs(String[] args, int expectedSize) {
        if (args.length < expectedSize) {
            throw new IllegalArgumentException("Not enough arguments for action: " + args[0]);
        }
    }
}
