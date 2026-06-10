package com.coresolution.csm.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for case-sensitive view/template mismatches.
 *
 * Bug: a controller returned view name "design/notices" while the template file
 * was "Notices.html". macOS (case-insensitive) resolved it fine, but Linux prod
 * (case-sensitive) threw TemplateInputException → 500 for institution users.
 *
 * This test scans controller `return "design/..."` / `return "csm/..."` literals
 * and fails if any resolves to a template that exists only when ignoring case —
 * i.e., the exact bug class that ships green from a Mac and breaks on Linux.
 */
class ViewTemplateCasingTest {

    private static final Path CONTROLLERS =
            Path.of("src/main/java/com/coresolution/csm/controller");
    private static final Path TEMPLATES =
            Path.of("src/main/resources/templates");
    private static final Pattern RETURN_VIEW =
            Pattern.compile("return\\s+\"((?:design|csm)/[A-Za-z0-9_/-]+)\"");

    @Test
    void noticesViewResolvesWithExactCase() {
        // The specific bug: design/notices must map to an exactly-cased file.
        assertThat(existsExact(TEMPLATES, "design/notices.html"))
                .as("templates/design/notices.html must exist with exact (lowercase) casing")
                .isTrue();
    }

    @Test
    void controllerViewNamesHaveExactCaseTemplates() throws IOException {
        List<String> mismatches = new ArrayList<>();
        for (String view : collectReturnedViews()) {
            String rel = view + ".html";
            // Only flag the case-only mismatch (loads on Mac, 500 on Linux).
            // Views with no template at all are out of scope (dynamic/fragment/typo).
            if (existsIgnoreCase(TEMPLATES, rel) && !existsExact(TEMPLATES, rel)) {
                mismatches.add(view + " -> " + actualCasing(TEMPLATES, rel));
            }
        }
        assertThat(mismatches)
                .as("controller view names must match template filenames exactly (case-sensitive on Linux prod)")
                .isEmpty();
    }

    private List<String> collectReturnedViews() throws IOException {
        List<String> views = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CONTROLLERS)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = RETURN_VIEW.matcher(Files.readString(java));
                while (m.find()) {
                    views.add(m.group(1));
                }
            }
        }
        return views;
    }

    /** Exact, case-sensitive existence even on a case-insensitive filesystem. */
    private static boolean existsExact(Path base, String relative) {
        Path current = base;
        for (String segment : relative.split("/")) {
            if (!listNames(current).contains(segment)) return false;
            current = current.resolve(segment);
        }
        return true;
    }

    private static boolean existsIgnoreCase(Path base, String relative) {
        Path current = base;
        for (String segment : relative.split("/")) {
            String match = listNames(current).stream()
                    .filter(n -> n.equalsIgnoreCase(segment))
                    .findFirst().orElse(null);
            if (match == null) return false;
            current = current.resolve(match);
        }
        return true;
    }

    private static String actualCasing(Path base, String relative) {
        Path current = base;
        StringBuilder sb = new StringBuilder();
        for (String segment : relative.split("/")) {
            String match = listNames(current).stream()
                    .filter(n -> n.equalsIgnoreCase(segment))
                    .findFirst().orElse(segment);
            sb.append(match).append('/');
            current = current.resolve(match);
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static List<String> listNames(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
