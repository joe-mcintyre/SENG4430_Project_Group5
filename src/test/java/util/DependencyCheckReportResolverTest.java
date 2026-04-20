package util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.util.DependencyCheckReportResolver;

class DependencyCheckReportResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void usesTheSuppliedReportWhenItAlreadyExists() throws Exception {
        Path sourceRoot = tempDir.resolve("project").resolve("src").resolve("main").resolve("java");
        Files.createDirectories(sourceRoot);

        Path suppliedReport = tempDir.resolve("dependency-check-report.json");
        Files.writeString(suppliedReport, "{\"dependencies\":[]}");

        Path resolvedReport = DependencyCheckReportResolver.ensureFreshReportExists(sourceRoot, suppliedReport);

        assertEquals(suppliedReport, resolvedReport);
    }

    @Test
    void findsTheNearestProjectRootFromASourceFolder() throws Exception {
        Path projectRoot = tempDir.resolve("sample-project");
        Path sourceRoot = projectRoot.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(sourceRoot);
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");

        Method findProjectRoot = DependencyCheckReportResolver.class.getDeclaredMethod("findProjectRoot", Path.class);
        findProjectRoot.setAccessible(true);

        Path resolvedRoot = (Path) findProjectRoot.invoke(null, sourceRoot);

        assertEquals(projectRoot.toAbsolutePath().normalize(), resolvedRoot);
    }
}
