package com.tool.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.domain.Finding;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class HTMLReportWriter extends ReportWriter {

    private StringBuilder htmlContent;

    public HTMLReportWriter(Path reportPath) {
        super(reportPath);
        this.htmlContent = new StringBuilder();
    }

    public void writeReport(AuditResult auditResult) throws Exception {

        writeHTMLContent(auditResult);

        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>SQ Audit Report — Automotive</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@400;600;700;800&family=Barlow:wght@400;500;600&family=Share+Tech+Mono&display=swap" rel="stylesheet">
                    <style>
                        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

                        :root {
                            --bg:            #0c0d0e;
                            --surface:       #131517;
                            --surface2:      #1a1c1f;
                            --surface3:      #212427;
                            --border:        rgba(255,255,255,0.06);
                            --border-md:     rgba(255,255,255,0.11);
                            --border-hi:     rgba(255,255,255,0.18);

                            --text:          #dde1e6;
                            --text-muted:    #5a6170;
                            --text-dim:      #3d4450;

                            --amber:         #f0a500;
                            --amber-dim:     rgba(240,165,0,0.10);
                            --amber-glow:    rgba(240,165,0,0.25);

                            --critical:      #e8372a;
                            --critical-dim:  rgba(232,55,42,0.12);
                            --critical-glow: rgba(232,55,42,0.30);

                            --major:         #e86c2a;
                            --major-dim:     rgba(232,108,42,0.12);
                            --major-glow:    rgba(232,108,42,0.22);

                            --minor:         #d4aa30;
                            --minor-dim:     rgba(212,170,48,0.12);

                            --info:          #4a90c4;
                            --info-dim:      rgba(74,144,196,0.12);

                            --pass:          #3a9e6e;
                            --pass-dim:      rgba(58,158,110,0.10);

                            --mono: 'Share Tech Mono', monospace;
                            --cond: 'Barlow Condensed', sans-serif;
                            --body: 'Barlow', sans-serif;
                            --radius: 3px;
                        }

                        html { scroll-behavior: smooth; }

                        body {
                            font-family: var(--body);
                            background: var(--bg);
                            color: var(--text);
                            min-height: 100vh;
                        }

                        /* ── Top bar ───────────────────────────────── */
                        .topbar {
                            background: var(--surface);
                            border-bottom: 1px solid var(--border-md);
                            padding: 0 40px;
                            height: 52px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            position: sticky;
                            top: 0;
                            z-index: 200;
                        }

                        .topbar-brand {
                            display: flex;
                            align-items: center;
                            gap: 14px;
                        }

                        .topbar-logo {
                            width: 26px;
                            height: 26px;
                            border: 2px solid var(--amber);
                            border-radius: 50%;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            flex-shrink: 0;
                        }

                        .topbar-logo::after {
                            content: '';
                            width: 7px;
                            height: 7px;
                            background: var(--amber);
                            border-radius: 50%;
                        }

                        .topbar-title {
                            font-family: var(--cond);
                            font-size: 14px;
                            font-weight: 700;
                            letter-spacing: .14em;
                            text-transform: uppercase;
                            color: var(--text);
                        }

                        .topbar-meta {
                            font-family: var(--mono);
                            font-size: 11px;
                            color: var(--text-muted);
                            letter-spacing: .06em;
                        }

                        /* ── Page layout ───────────────────────────── */
                        .layout {
                            display: grid;
                            grid-template-columns: 220px 1fr;
                            min-height: calc(100vh - 52px);
                        }

                        /* ── Sidebar ───────────────────────────────── */
                        .sidebar {
                            background: var(--surface);
                            border-right: 1px solid var(--border);
                            padding: 28px 0;
                            position: sticky;
                            top: 52px;
                            height: calc(100vh - 52px);
                            overflow-y: auto;
                        }

                        .sidebar::-webkit-scrollbar { width: 4px; }
                        .sidebar::-webkit-scrollbar-track { background: transparent; }
                        .sidebar::-webkit-scrollbar-thumb { background: var(--border-md); border-radius: 99px; }

                        .sidebar-label {
                            font-family: var(--mono);
                            font-size: 9px;
                            letter-spacing: .18em;
                            text-transform: uppercase;
                            color: var(--text-dim);
                            padding: 0 20px 12px;
                        }

                        .sidebar-link {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            padding: 9px 20px;
                            font-family: var(--cond);
                            font-size: 13px;
                            font-weight: 600;
                            letter-spacing: .06em;
                            text-transform: uppercase;
                            color: var(--text-muted);
                            text-decoration: none;
                            border-left: 2px solid transparent;
                            transition: color .15s, border-color .15s, background .15s;
                        }

                        .sidebar-link::before {
                            content: '';
                            width: 5px;
                            height: 5px;
                            border: 1px solid currentColor;
                            border-radius: 50%;
                            flex-shrink: 0;
                            opacity: .5;
                        }

                        .sidebar-link:hover {
                            color: var(--amber);
                            border-left-color: var(--amber);
                            background: var(--amber-dim);
                        }

                        /* ── Main content ──────────────────────────── */
                        .main {
                            padding: 40px 48px;
                            max-width: 1040px;
                        }

                        /* ── Page header ───────────────────────────── */
                        .page-header {
                            margin-bottom: 48px;
                            padding-bottom: 28px;
                            border-bottom: 1px solid var(--border);
                        }

                        .page-eyebrow {
                            display: flex;
                            align-items: center;
                            gap: 12px;
                            font-family: var(--mono);
                            font-size: 10px;
                            letter-spacing: .2em;
                            text-transform: uppercase;
                            color: var(--amber);
                            margin-bottom: 14px;
                        }

                        .page-eyebrow::after {
                            content: '';
                            width: 48px;
                            height: 1px;
                            background: var(--amber);
                            opacity: .5;
                        }

                        .page-title {
                            font-family: var(--cond);
                            font-size: 44px;
                            font-weight: 800;
                            letter-spacing: -.01em;
                            line-height: 1.05;
                            text-transform: uppercase;
                            color: #fff;
                        }

                        .page-title span { color: var(--amber); }

                        .page-sub {
                            margin-top: 10px;
                            font-size: 12px;
                            color: var(--text-muted);
                            font-family: var(--mono);
                            letter-spacing: .05em;
                        }

                        /* ── Category section ──────────────────────── */
                        .category-section { margin-bottom: 52px; }

                        .category-header {
                            display: flex;
                            align-items: center;
                            gap: 0;
                            margin-bottom: 6px;
                            scroll-margin-top: 72px;
                        }

                        .category-number {
                            font-family: var(--mono);
                            font-size: 10px;
                            color: var(--amber);
                            letter-spacing: .1em;
                            margin-right: 14px;
                            opacity: .7;
                        }

                        .category-title {
                            font-family: var(--cond);
                            font-size: 20px;
                            font-weight: 800;
                            letter-spacing: .08em;
                            text-transform: uppercase;
                            color: #fff;
                        }

                        .category-rule {
                            flex: 1;
                            height: 1px;
                            margin-left: 16px;
                            background: var(--border-md);
                        }

                        .category-desc {
                            font-size: 12px;
                            color: var(--text-muted);
                            margin-bottom: 18px;
                            line-height: 1.6;
                        }

                        /* ── Metric card ───────────────────────────── */
                        .metric-card {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-top: 2px solid var(--border-md);
                            border-radius: var(--radius);
                            padding: 22px 24px;
                            margin-bottom: 12px;
                            transition: border-color .2s, border-top-color .2s;
                        }

                        .metric-card:hover           { border-color: var(--border-md); border-top-color: var(--amber); }
                        .metric-card.sev-critical    { border-top-color: var(--critical); }
                        .metric-card.sev-major       { border-top-color: var(--major); }
                        .metric-card.sev-minor       { border-top-color: var(--minor); }
                        .metric-card.sev-info        { border-top-color: var(--info); }
                        .metric-card.sev-success     { border-top-color: var(--pass); }

                        .metric-header {
                            display: flex;
                            justify-content: space-between;
                            align-items: flex-start;
                            gap: 16px;
                            margin-bottom: 10px;
                            flex-wrap: wrap;
                        }

                        .metric-name {
                            font-family: var(--cond);
                            font-size: 17px;
                            font-weight: 700;
                            letter-spacing: .05em;
                            text-transform: uppercase;
                            color: #fff;
                        }

                        .badge-group { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

                        .badge {
                            display: inline-flex;
                            align-items: center;
                            gap: 6px;
                            padding: 3px 10px;
                            border-radius: 2px;
                            font-family: var(--mono);
                            font-size: 10px;
                            letter-spacing: .08em;
                            text-transform: uppercase;
                            border: 1px solid transparent;
                        }

                        .badge::before {
                            content: '';
                            width: 5px;
                            height: 5px;
                            border-radius: 50%;
                            background: currentColor;
                            flex-shrink: 0;
                        }

                        .badge-score {
                            background: var(--surface3);
                            border-color: var(--border-md);
                            color: var(--text-muted);
                        }

                        .badge-score::before { display: none; }

                        .badge-critical {
                            background: var(--critical-dim);
                            border-color: rgba(232,55,42,.4);
                            color: var(--critical);
                            box-shadow: 0 0 8px var(--critical-glow);
                        }

                        .badge-major {
                            background: var(--major-dim);
                            border-color: rgba(232,108,42,.4);
                            color: var(--major);
                            box-shadow: 0 0 8px var(--major-glow);
                        }

                        .badge-minor {
                            background: var(--minor-dim);
                            border-color: rgba(212,170,48,.4);
                            color: var(--minor);
                        }

                        .badge-info {
                            background: var(--info-dim);
                            border-color: rgba(74,144,196,.4);
                            color: var(--info);
                        }

                        .badge-success {
                            background: var(--pass-dim);
                            border-color: rgba(58,158,110,.4);
                            color: var(--pass);
                        }

                        .metric-description {
                            font-size: 13px;
                            color: var(--text-muted);
                            line-height: 1.65;
                            margin-bottom: 16px;
                        }

                        /* ── Findings table ────────────────────────── */
                        .findings-table-wrap {
                            border: 1px solid var(--border);
                            border-radius: var(--radius);
                            overflow-x: auto;
                        }

                        .findings-table-wrap::-webkit-scrollbar { height: 5px; }
                        .findings-table-wrap::-webkit-scrollbar-track { background: var(--surface2); }
                        .findings-table-wrap::-webkit-scrollbar-thumb { background: var(--border-md); border-radius: 99px; }
                        .findings-table-wrap::-webkit-scrollbar-thumb:hover { background: var(--border-hi); }

                        table { width: 100%; border-collapse: collapse; min-width: 520px; }

                        thead tr { background: var(--surface2); }

                        th {
                            font-family: var(--mono);
                            font-size: 9px;
                            letter-spacing: .16em;
                            text-transform: uppercase;
                            color: var(--text-dim);
                            padding: 9px 14px;
                            text-align: left;
                            border-bottom: 1px solid var(--border);
                            white-space: nowrap;
                        }

                        td {
                            font-family: var(--mono);
                            font-size: 12px;
                            color: var(--text);
                            padding: 9px 14px;
                            border-bottom: 1px solid var(--border);
                            vertical-align: top;
                        }

                        tbody tr:last-child td { border-bottom: none; }
                        tbody tr:hover td { background: rgba(255,255,255,.02); }

                        td:first-child { color: var(--amber); }
                        td:nth-child(2) { color: var(--text-muted); width: 56px; }
                        td:nth-child(3) { color: var(--info); }

                        /* ── Empty state ───────────────────────────── */
                        .empty {
                            text-align: center;
                            padding: 80px 40px;
                            color: var(--text-dim);
                            font-family: var(--mono);
                            font-size: 12px;
                            letter-spacing: .1em;
                        }

                        /* ── Go to top ─────────────────────────────── */
                        .go-top {
                            position: fixed;
                            bottom: 28px;
                            right: 28px;
                            width: 38px;
                            height: 38px;
                            background: var(--surface2);
                            border: 1px solid var(--amber);
                            border-radius: 2px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            cursor: pointer;
                            text-decoration: none;
                            color: var(--amber);
                            font-family: var(--mono);
                            font-size: 14px;
                            opacity: 0;
                            pointer-events: none;
                            transform: translateY(8px);
                            transition: opacity .2s, transform .2s, box-shadow .2s, background .2s;
                            z-index: 300;
                        }

                        .go-top.visible { opacity: 1; pointer-events: all; transform: translateY(0); }
                        .go-top:hover   { box-shadow: 0 0 18px var(--amber-glow); background: var(--amber-dim); }
                    </style>
                </head>
                <body>

                    <div class="topbar">
                        <div class="topbar-brand">
                            <div class="topbar-logo"></div>
                            <span class="topbar-title">Software Quality Audit System</span>
                        </div>
                    </div>

                    <div class="layout">
                        <nav class="sidebar" id="sidebar">
                            <div class="sidebar-label">Inspection Modules</div>
                """);

        html.append(htmlContent);

        html.append("""
                    </div>

                    <a href="#" class="go-top" id="goTop" aria-label="Back to top">&#8593;</a>

                    <script>
                        const btn = document.getElementById('goTop');
                        window.addEventListener('scroll', () => {
                            btn.classList.toggle('visible', window.scrollY > 300);
                        });
                        btn.addEventListener('click', e => {
                            e.preventDefault();
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                        });
                    </script>
                </body>
                </html>
                """);

        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }

        Files.writeString(reportPath, html.toString());
    }

    private ArrayList<Category> getCategories(AuditResult auditResult) {
        List<MetricResult> results = auditResult.results();
        ArrayList<Category> categories = new ArrayList<>();

        Category currentCategory = null;
        for (MetricResult metricResult : results) {
            Metric metric = metricResult.metric();
            if (!metric.category().equals(currentCategory)) {
                currentCategory = metric.category();
                categories.add(currentCategory);
            }
        }

        return categories;
    }

    private void writeHTMLContent(AuditResult auditResult) {

        List<MetricResult> results = auditResult.results();

        if (results.isEmpty()) {
            htmlContent.append("</nav><main class='main'>");
            htmlContent.append("<div class='empty'>No project data</div>");
            htmlContent.append("</main>");
            return;
        }

        ArrayList<Category> categories = getCategories(auditResult);

        // Sidebar links
        int idx = 0;
        for (Category category : categories) {
            idx++;
            htmlContent.append("<a href='#cat-")
                       .append(category.name().replace(" ", "-"))
                       .append("' class='sidebar-link'>")
                       .append(String.format("%02d", idx))
                       .append(" &nbsp;")
                       .append(category.name())
                       .append("</a>");
        }

        // Close sidebar, open main
        htmlContent.append("</nav><main class='main'>");

        // Page header
        htmlContent.append("""
                <div class='page-header'>
                    <div class='page-eyebrow'>Diagnostic Report</div>
                    <div class='page-title'>Software Quality<br><span>Audit Report</span></div>
                </div>
                """);

        // Metric cards
        Category currentCategory = null;
        int catIdx = 0;

        for (MetricResult metricResult : results) {
            Metric metric = metricResult.metric();

            if (!metric.category().equals(currentCategory)) {
                if (currentCategory != null) {
                    htmlContent.append("</div>"); // close previous category-section
                }
                currentCategory = metric.category();
                catIdx++;

                htmlContent.append("<div class='category-section'>");
                htmlContent.append("<div class='category-header' id='cat-")
                           .append(currentCategory.name().replace(" ", "-"))
                           .append("'>");
                htmlContent.append("<span class='category-number'>")
                           .append(String.format("%02d", catIdx))
                           .append("</span>");
                htmlContent.append("<span class='category-title'>")
                           .append(currentCategory.name())
                           .append("</span>");
                htmlContent.append("<div class='category-rule'></div>");
                htmlContent.append("</div>"); // category-header
                htmlContent.append("<div class='category-desc'>")
                           .append(currentCategory.description())
                           .append("</div>");
            }

            Threshold highestThreshold = metricResult.mostSevereThreshold();
            String sevClass = highestThreshold == null
                ? "sev-success"
                : "sev-" + highestThreshold.toString().toLowerCase();

            htmlContent.append("<div class='metric-card ").append(sevClass).append("'>");

            htmlContent.append("<div class='metric-header'>");
            htmlContent.append("<div class='metric-name'>").append(metric.name()).append("</div>");

            htmlContent.append("<div class='badge-group'>");
            htmlContent.append("<span class='badge badge-score'>Score: ")
                       .append(String.format("%.2f", metricResult.score()))
                       .append("</span>");

            String badgeClass = highestThreshold == null ? "success" : highestThreshold.toString().toLowerCase();
            String badgeText  = highestThreshold == null ? "Pass" : highestThreshold.toString();

            htmlContent.append("<span class='badge badge-").append(badgeClass).append("'>")
                       .append(badgeText)
                       .append("</span>");
            htmlContent.append("</div>"); // badge-group
            htmlContent.append("</div>"); // metric-header

            htmlContent.append("<p class='metric-description'>").append(metric.description()).append("</p>");

            if (!metricResult.findings().isEmpty()) {
                htmlContent.append("<div class='findings-table-wrap'>");
                htmlContent.append("<table>");
                htmlContent.append("<thead><tr>")
                           .append("<th>Source File</th>")
                           .append("<th>Line</th>")
                           .append("<th>Function / Method</th>")
                           .append("<th>Diagnostic Message</th>")
                           .append("</tr></thead>");
                htmlContent.append("<tbody>");

                for (Finding finding : metricResult.findings()) {
                    htmlContent.append("<tr>");
                    htmlContent.append("<td>").append(finding.file()).append("</td>");
                    htmlContent.append("<td>").append(finding.line()).append("</td>");
                    htmlContent.append("<td>").append(finding.function()).append("</td>");
                    htmlContent.append("<td>").append(finding.message()).append("</td>");
                    htmlContent.append("</tr>");
                }

                htmlContent.append("</tbody></table></div>");
            }

            htmlContent.append("</div>"); // metric-card
        }

        if (currentCategory != null) {
            htmlContent.append("</div>"); // close final category-section
        }

        htmlContent.append("</main>");
    }
}