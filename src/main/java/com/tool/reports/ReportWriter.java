package com.tool.reports;

import java.nio.file.Path;

import com.tool.app.AuditResult;

public abstract class ReportWriter {
    protected final Path reportPath;

    public ReportWriter(Path reportPath){
        this.reportPath = reportPath;
    }

    abstract void writeReport(AuditResult auditResult) throws Exception;
}