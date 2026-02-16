package com.tool.util;

import com.tool.domain.LocStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class JavaLocCounter {

    public LocStats count(Path sourceRoot) throws IOException {
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            throw new IllegalArgumentException("Source root not found: " + sourceRoot);
        }

        AtomicLong fileCount = new AtomicLong(0);
        AtomicLong logicalLoc = new AtomicLong(0);

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        fileCount.incrementAndGet();
                        try {
                            logicalLoc.addAndGet(countLogicalLocInFile(path));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed reading " + path, e);
                        }
                    });
        }

        return new LocStats(fileCount.get(), logicalLoc.get());
    }

    private long countLogicalLocInFile(Path javaFile) throws IOException {
        List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
        long count = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            ParseState parsed = parseLine(line, inBlockComment);
            inBlockComment = parsed.inBlockComment;
            if (parsed.hasCode) {
                count++;
            }
        }

        return count;
    }

    private ParseState parseLine(String line, boolean inBlockComment) {
        boolean inString = false;
        char stringDelimiter = '\0';
        boolean escaped = false;
        boolean hasCode = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (!inString && c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }

            if (!inString && c == '/' && next == '/') {
                break;
            }

            if ((c == '"' || c == '\'') && !escaped) {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
            }

            escaped = (c == '\\') && !escaped;

            if (!Character.isWhitespace(c)) {
                hasCode = true;
            }
        }

        return new ParseState(inBlockComment, hasCode);
    }

    private static final class ParseState {
        private final boolean inBlockComment;
        private final boolean hasCode;

        private ParseState(boolean inBlockComment, boolean hasCode) {
            this.inBlockComment = inBlockComment;
            this.hasCode = hasCode;
        }
    }
}
