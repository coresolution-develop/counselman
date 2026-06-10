package com.coresolution.csm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.coresolution.csm.vo.Category3;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the Alpine inpatient-consultation dynamic-field options.
 *
 * Bug: the dynamic-category JSON builder produced option labels via
 * Category3.getCc_col_03AsString() (the comma-joined CSV), so a select field
 * like 배뇨/배변 with options "혼자가능,부분적도움필요,전혀불가능" collapsed into a
 * SINGLE &lt;option&gt;. The Alpine x-for then rendered one comma-joined row.
 *
 * Fix: PageController.buildSelectOptions iterates the split cc_col_03 list (and
 * defensively re-splits any remaining CSV) so each value is its own option.
 */
class PageControllerDynamicOptionsTest {

    private static Category3 opt(String csv) {
        Category3 c = new Category3();
        c.setCc_col_03FromString(csv); // mirrors CsmAuthService load-time conversion
        return c;
    }

    @Test
    void csvBackedOption_splitsIntoSeparateOptions() {
        List<String> options = PageController.buildSelectOptions(
                List.of(opt("혼자가능,부분적도움필요,전혀불가능")), "배뇨/배변");

        assertThat(options).containsExactly("혼자가능", "부분적도움필요", "전혀불가능");
        assertThat(options).doesNotContain("혼자가능,부분적도움필요,전혀불가능");
    }

    @Test
    void multipleSingleValueRows_eachBecomeAnOption() {
        List<String> options = PageController.buildSelectOptions(
                List.of(opt("명료"), opt("기면"), opt("혼미"), opt("반혼수"), opt("혼수")), "의식상태");

        assertThat(options).containsExactly("명료", "기면", "혼미", "반혼수", "혼수");
    }

    @Test
    void fieldLabelItself_isExcluded() {
        List<String> options = PageController.buildSelectOptions(
                List.of(opt("의식상태"), opt("명료")), "의식상태");

        assertThat(options).containsExactly("명료");
    }

    @Test
    void nullOrEmptyOptions_yieldEmptyList() {
        assertThat(PageController.buildSelectOptions(null, "x")).isEmpty();
        assertThat(PageController.buildSelectOptions(List.of(), "x")).isEmpty();
    }

    @Test
    void splitOptionLabels_trimsAndDropsBlanks() {
        assertThat(PageController.splitOptionLabels(" a , b ,, c "))
                .containsExactly("a", "b", "c");
        assertThat(PageController.splitOptionLabels(null)).isEmpty();
        assertThat(PageController.splitOptionLabels("")).isEmpty();
    }
}
