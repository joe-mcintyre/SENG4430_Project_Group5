package com.tool.scan;

import com.tool.domain.Finding;
import com.tool.domain.Severity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpotBugsXmlFindingProvider implements FindingProvider {

    @Override
    public List<Finding> load(Path reportPath) throws Exception {
        if (reportPath == null || !Files.exists(reportPath)) {
            throw new IllegalArgumentException("SpotBugs report not found: " + reportPath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(reportPath.toFile());

        NodeList bugs = document.getElementsByTagName("BugInstance");
        List<Finding> findings = new ArrayList<>(bugs.getLength());

        for (int i = 0; i < bugs.getLength(); i++) {
            Element bug = (Element) bugs.item(i);

            String ruleId = emptyToNull(bug.getAttribute("type"));
            Integer rank = parseIntOrNull(bug.getAttribute("rank"));
            Integer priority = parseIntOrNull(bug.getAttribute("priority"));

            Severity severity = Severity.fromSpotBugs(rank, priority);
            String message = firstText(bug, "LongMessage", "ShortMessage");
            if (message == null) {
                message = ruleId != null ? ruleId : "SpotBugs finding";
            }

            SourceRef sourceRef = extractSourceRef(bug);
            findings.add(new Finding(
                    "SpotBugs",
                    ruleId != null ? ruleId : "UNKNOWN_RULE",
                    severity,
                    message,
                    sourceRef.file,
                    sourceRef.line
            ));
        }

        return findings;
    }

    private SourceRef extractSourceRef(Element bug) {
        NodeList sourceLines = bug.getElementsByTagName("SourceLine");
        for (int i = 0; i < sourceLines.getLength(); i++) {
            Element source = (Element) sourceLines.item(i);
            String sourcePath = emptyToNull(source.getAttribute("sourcepath"));
            String className = emptyToNull(source.getAttribute("classname"));

            String file = sourcePath;
            if (file == null && className != null) {
                file = className.replace('.', '/') + ".java";
            }

            Integer line = parseIntOrNull(source.getAttribute("start"));
            if (file != null || line != null) {
                return new SourceRef(file, line);
            }
        }

        return new SourceRef(null, null);
    }

    private String firstText(Element parent, String... tags) {
        for (String tag : tags) {
            NodeList list = parent.getElementsByTagName(tag);
            if (list.getLength() > 0) {
                String value = list.item(0).getTextContent();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private String emptyToNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    private Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class SourceRef {
        private final String file;
        private final Integer line;

        private SourceRef(String file, Integer line) {
            this.file = file;
            this.line = line;
        }
    }
}
