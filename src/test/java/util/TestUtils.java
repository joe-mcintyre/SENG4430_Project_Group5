package test.java.util;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestUtils {
  public static void writeMinimalPom(Path root) throws Exception {
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project/>");
  }
}
