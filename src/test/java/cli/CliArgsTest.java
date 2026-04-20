package cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tool.cli.CliArgs;

class CliArgsTest {

    @Test
    void usesDefaultsWhenOnlySourceIsProvided() {
        CliArgs args = CliArgs.parse(new String[] {"--source", "demo-src"});

        assertEquals("demo-src", args.projectName());
        assertEquals(Path.of("demo-src"), args.sourceRoot());
        assertNull(args.dependencyReportPath());
        assertNotNull(args.configPath());
        assertTrue(Files.exists(args.configPath()));
        assertEquals(Path.of("reports", "quality-report"), args.outputPath());
    }

    @Test
    void acceptsShortSourceAndExplicitOptionalValues() {
        CliArgs args = CliArgs.parse(new String[] {
            "-s", "sample-project",
            "-d", "reports/dep-check.json",
            "--config", "config1.json",
            "--output", "out/custom-report",
            "--project", "Sample Project"
        });

        assertEquals("Sample Project", args.projectName());
        assertEquals(Path.of("sample-project"), args.sourceRoot());
        assertEquals(Path.of("reports", "dep-check.json"), args.dependencyReportPath());
        assertNotNull(args.configPath());
        assertTrue(Files.exists(args.configPath()));
        assertEquals(Path.of("out", "custom-report"), args.outputPath());
    }

    @Test
    void rejectsMissingSourceArgument() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> CliArgs.parse(new String[] {"--project", "Sample"})
        );

        assertTrue(error.getMessage().contains("--source is required"));
    }
}
