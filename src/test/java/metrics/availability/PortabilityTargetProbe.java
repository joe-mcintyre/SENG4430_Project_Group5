package metrics.availability;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PortabilityTargetProbe {
    private PortabilityTargetProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <write|assert-missing|assert-exists> <path>");
        }

        Path target = Path.of(args[1]);

        switch (args[0]) {
            case "write" -> Files.writeString(target, "marker");
            case "assert-missing" -> {
                if (Files.exists(target)) {
                    System.err.println("Expected file to be missing: " + target);
                    System.exit(1);
                }
            }
            case "assert-exists" -> {
                if (!Files.exists(target)) {
                    System.err.println("Expected file to exist: " + target);
                    System.exit(1);
                }
            }
            default -> throw new IllegalArgumentException("Unknown action: " + args[0]);
        }
    }
}
