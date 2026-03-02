package com.tool.metrics.security.scan;

import com.tool.domain.Finding;
import com.tool.domain.Severity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Parses an OWASP Dependency-Check XML report into domain Findings.
 *
 * Output:
 *  - One Finding per <vulnerability> entry
 *  - severity mapped into {CRITICAL, MAJOR, MINOR, INFO}
 *  - message includes CVE/name and optional title
 *  - file field is a dependency identifier (GAV or filename/path)
 *  - line is null (not applicable)
 */
public class DependencyCheckXmlParser {

    public ArrayList<Finding> parse(Path reportPath) throws IOException {
        if (reportPath == null) throw new IllegalArgumentException("reportPath cannot be null");
        if (!Files.exists(reportPath)) throw new IOException("Dependency-Check report not found: " + reportPath);

        try (InputStream in = Files.newInputStream(reportPath)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            // Basic hardening (safe defaults)
            try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}

            Document doc = dbf.newDocumentBuilder().parse(in);
            doc.getDocumentElement().normalize();

            ArrayList<Finding> findings = new ArrayList<>();

            // Dependency-Check typically has many <dependency> nodes.
            NodeList dependencyNodes = doc.getElementsByTagNameNS("*", "dependency");
            for (int i = 0; i < dependencyNodes.getLength(); i++) {
                Element dep = asElement(dependencyNodes.item(i));
                if (dep == null) continue;

                String dependencyId = bestDependencyId(dep);

                NodeList vulnNodes = dep.getElementsByTagNameNS("*", "vulnerability");
                for (int v = 0; v < vulnNodes.getLength(); v++) {
                    Element vuln = asElement(vulnNodes.item(v));
                    if (vuln == null) continue;

                    String sevRaw = textOfFirstChild(vuln, "severity");
                    Severity severity = mapSeverity(sevRaw);

                    String name = textOfFirstChild(vuln, "name");   // often CVE-...
                    String title = textOfFirstChild(vuln, "title"); // sometimes present
                    String message = buildMessage(name, title);

                    findings.add(new Finding(severity, message, dependencyId, null));
                }
            }

            return findings;
        } catch (SAXException e) {
            throw new IOException("Failed to parse Dependency-Check XML: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Failed to load Dependency-Check XML: " + e.getMessage(), e);
        }
    }

    private Element asElement(Node n) {
        return (n instanceof Element e) ? e : null;
    }

    private String textOfFirstChild(Element parent, String localName) {
        NodeList list = parent.getElementsByTagNameNS("*", localName);
        if (list.getLength() == 0) return null;
        Node n = list.item(0);
        String txt = (n == null) ? null : n.getTextContent();
        if (txt == null) return null;
        txt = txt.trim();
        return txt.isEmpty() ? null : txt;
    }

    private String buildMessage(String name, String title) {
        if (name == null && title == null) return "Vulnerability (no name/title)";
        if (name != null && title != null && !title.equalsIgnoreCase(name)) return name + ": " + title;
        return (name != null) ? name : title;
    }

    private String bestDependencyId(Element dep) {
        // Try Maven-like identifiers if present
        String groupId = textOfFirstChild(dep, "groupId");
        String artifactId = textOfFirstChild(dep, "artifactId");
        String version = textOfFirstChild(dep, "version");

        String gav = null;
        if (groupId != null && artifactId != null) {
            gav = groupId + ":" + artifactId + (version != null ? ":" + version : "");
        }

        // Fall back to fileName/filePath
        String fileName = textOfFirstChild(dep, "fileName");
        String filePath = textOfFirstChild(dep, "filePath");

        if (gav != null && fileName != null) return gav + " (" + fileName + ")";
        if (gav != null) return gav;
        if (fileName != null) return fileName;
        if (filePath != null) return filePath;

        return "dependency (unknown)";
    }

    private Severity mapSeverity(String depCheckSeverity) {
        if (depCheckSeverity == null) return Severity.INFO;
        String s = depCheckSeverity.trim().toUpperCase();

        // Common Dependency-Check values: CRITICAL/HIGH/MEDIUM/LOW (and sometimes INFO/UNKNOWN)
        return switch (s) {
            case "CRITICAL", "HIGH" -> Severity.CRITICAL;
            case "MEDIUM" -> Severity.MAJOR;
            case "LOW" -> Severity.MINOR;
            default -> Severity.INFO;
        };
    }
}