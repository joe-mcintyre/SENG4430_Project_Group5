package com.tool.util;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceUtil {

    /**
     * Get a Path to a resource inside src/main/resources or src/test/resources.
     * If running from a jar, it copies the resource to a temp file.
     *
     * @param resourceName name of the resource (e.g., "file.txt" or "config/config.yaml")
     * @return Path to the resource
     * @throws IOException if resource is missing or cannot be copied
     */
    public static Path getResourcePath(String resourceName) {
        try {
            if (resourceName == null || resourceName.isBlank()) {
                return null;
            }

            String normalizedName = resourceName.replace('\\', '/');
            InputStream is = ResourceUtil.class.getClassLoader().getResourceAsStream(normalizedName);

            if (is == null) {
                return null;
            }

            // Create temp file with same extension as resource
            String suffix = "";
            int dotIndex = normalizedName.lastIndexOf('.');
            if (dotIndex >= 0) {
                suffix = normalizedName.substring(dotIndex);
            }

            Path tempFile = Files.createTempFile("resource-", suffix);
            tempFile.toFile().deleteOnExit(); // optional: auto-delete on JVM exit

            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return tempFile;
        } 
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
