package util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.tool.util.ResourceUtil;

public class ResourceUtilTest {

    @Test
    void resolvesResourcesWithWindowsSeparators() {
        assertNotNull(ResourceUtil.getResourcePath(
            "metrics\\maintainability\\cyclomatic_complexity\\Empty.java"
        ));
    }

    @Test
    void resolvesResourcesWithUnixSeparators() {
        assertNotNull(ResourceUtil.getResourcePath(
            "metrics/maintainability/cyclomatic_complexity/Empty.java"
        ));
    }
}
