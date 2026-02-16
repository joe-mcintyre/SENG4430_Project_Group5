package com.tool.scan;

import com.tool.domain.Finding;

import java.nio.file.Path;
import java.util.List;

public interface FindingProvider {
    List<Finding> load(Path reportPath) throws Exception;
}
