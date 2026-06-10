package com.coresolution.csm.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.coresolution.csm.vo.Category3;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Regression guard for the consultation-form select box bug.
 *
 * Bug: a select-box field stores its options as one CSV string in
 * counsel_category3.cc_col_03 ("명료,기면,혼미,반혼수,혼수"). The form templates
 * (csm/counsel/new.html, newMobile.html) rendered cc_col_03AsString — the whole
 * CSV — as the label of a SINGLE <option>, so all options collapsed into one row.
 *
 * Fix: iterate opt.cc_col_03 (the split List) and emit one <option> per element.
 * This test renders the fixed select-loop and asserts the options are separated.
 */
class CounselSelectOptionsRenderTest {

    /** The exact select-loop used by new.html / newMobile.html after the fix. */
    private static final String SELECT_LOOP =
            "<select th:name=\"|${fieldKey}_select|\">"
          + "<option value=\"\">선택하세요</option>"
          + "<th:block th:each=\"opt : ${opts}\">"
          + "<th:block th:each=\"label : ${opt.cc_col_03}\">"
          + "<option th:if=\"${!#strings.isEmpty(label) and #strings.trim(label) != #strings.trim(c2.cc_col_02)}\""
          + " th:value=\"${label}\" th:text=\"${label}\""
          + " th:selected=\"${valueMap[fieldKey + '_select'] == label}\"></option>"
          + "</th:block></th:block>"
          + "</select>";

    private static TemplateEngine engine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Category3 optionGroup(String csv) {
        Category3 opt = new Category3();
        opt.setCc_col_03FromString(csv); // mirrors CsmAuthService load-time conversion
        return opt;
    }

    @Test
    void csvBackedOptions_renderAsSeparateOptions() {
        Context ctx = new Context();
        ctx.setVariable("fieldKey", "field_1_2");
        ctx.setVariable("c2", Map.of("cc_col_02", "의식상태"));
        ctx.setVariable("valueMap", Map.of());
        ctx.setVariable("opts", List.of(optionGroup("명료,기면,혼미,반혼수,혼수")));

        String html = engine().process(SELECT_LOOP, ctx);

        // Each option is its own <option> ...
        assertThat(html).contains(">명료</option>");
        assertThat(html).contains(">기면</option>");
        assertThat(html).contains(">혼미</option>");
        assertThat(html).contains(">반혼수</option>");
        assertThat(html).contains(">혼수</option>");

        // ... and NOT collapsed into a single joined option (the bug).
        assertThat(html).doesNotContain(">명료,기면,혼미,반혼수,혼수</option>");

        // 5 real options + 1 "선택하세요" placeholder.
        assertThat(countOccurrences(html, "<option ")).isEqualTo(6);
        assertThat(countOccurrences(html, ">선택하세요</option>")).isEqualTo(1);
    }

    @Test
    void selectedValue_marksOnlyMatchingOption() {
        Context ctx = new Context();
        ctx.setVariable("fieldKey", "field_1_2");
        ctx.setVariable("c2", Map.of("cc_col_02", "의식상태"));
        ctx.setVariable("valueMap", Map.of("field_1_2_select", "혼미"));
        ctx.setVariable("opts", List.of(optionGroup("명료,기면,혼미,반혼수,혼수")));

        String html = engine().process(SELECT_LOOP, ctx);

        assertThat(html).contains("selected=\"selected\"");
        assertThat(countOccurrences(html, "selected=\"selected\"")).isEqualTo(1);
        assertThat(html).contains("value=\"혼미\" selected=\"selected\"");
    }

    /** Single-value option (one row per option, empty CSV split) still renders one option. */
    @Test
    void singleValueOption_rendersOneOption() {
        Context ctx = new Context();
        ctx.setVariable("fieldKey", "field_1_2");
        ctx.setVariable("c2", Map.of("cc_col_02", "의식상태"));
        ctx.setVariable("valueMap", Map.of());
        ctx.setVariable("opts", List.of(optionGroup("명료"), optionGroup("혼수")));

        String html = engine().process(SELECT_LOOP, ctx);

        assertThat(countOccurrences(html, "<option ")).isEqualTo(3); // 2 real + placeholder
        assertThat(html).contains(">명료</option>");
        assertThat(html).contains(">혼수</option>");
    }

    /**
     * Guards the actual template files against reverting to the joined-CSV label.
     * The select-box options must be iterated as a list (opt.cc_col_03), never
     * rendered via cc_col_03AsString (which collapses all options into one).
     */
    @Test
    void counselFormTemplates_iterateOptionListNotJoinedCsv() throws IOException {
        for (String template : List.of(
                "src/main/resources/templates/csm/counsel/new.html",
                "src/main/resources/templates/csm/counsel/newMobile.html")) {
            String html = Files.readString(Path.of(template));
            assertThat(html)
                    .as("%s must iterate the split option list", template)
                    .contains("th:each=\"label : ${opt.cc_col_03}\"");
            assertThat(html)
                    .as("%s must not use the joined CSV as an option label", template)
                    .doesNotContain("cc_col_03AsString");
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
