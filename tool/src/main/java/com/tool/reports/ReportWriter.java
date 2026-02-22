package com.tool.reports;

import com.tool.metrics.MetricResult;

public interface ReportWriter {
    void appendMetadata(String metadata);
    void appendResult(MetricResult result);
    void writeReport() throws Exception;
}