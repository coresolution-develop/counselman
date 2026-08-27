package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 기관코드 형식 검증과 정규화.
 *
 * <p>── 왜 좁게 막나 ──
 * 코드가 <b>테이블 이름에 그대로 박힌다</b>. 등록 후에는 사실상 바꿀 수 없으므로
 * <b>입력 시점이 유일한 방어선</b>이다.
 *
 * <p>실제로 {@code hsop_0001} 이 검증 없이 들어와 테이블이 두 표기로 갈라졌다
 * (약 30쌍). 기능마다 어느 표기로 만들어졌는지가 달라
 * {@code messages_log_hsop_0001} 은 소문자에만, {@code received_sms_HSOP_0001} 은
 * 대문자에만 있었다. 살아 있는 기관에서 같은 일이 나면 <b>데이터가 두 곳으로 나뉜다.</b>
 */
class InstCodeValidationTest {

    /**
     * <b>기존 운영 코드가 전부 통과해야 한다.</b>
     * 하나라도 막히면 그 기관이 등록·동기화 불가가 된다.
     */
    @ParameterizedTest
    @ValueSource(strings = { "COHS", "DCHS", "FALH", "HSFH", "HSJH", "SLAH", "SLOM", "TEST" })
    void 기존_prod_코드가_전부_통과한다(String code) {
        assertThatCode(() -> CsmSchemaBootstrapService.requireValidInstCode(code))
                .doesNotThrowAnyException();
        assertThat(CsmSchemaBootstrapService.requireValidInstCode(code)).isEqualTo(code);
    }

    @Test
    void core_는_예약어로_소문자를_유지한다() {
        // 대문자로 저장하면 SUPER 권한 판정이 어긋난다.
        assertThat(CsmSchemaBootstrapService.requireValidInstCode("core")).isEqualTo("core");
    }

    /**
     * ⭐ 실제로 문제를 만든 값이다.
     *
     * <p><b>정규화로 통과시키지 않는다.</b> 소문자를 대문자로 바꿔 받아 주면
     * 형식이 계속 다양해지고, 그 값이 테이블 이름에 박힌 뒤에는 되돌릴 수 없다.
     */
    @Test
    void hsop_0001_은_거부된다() {
        assertThatThrownBy(() -> CsmSchemaBootstrapService.requireValidInstCode("hsop_0001"))
                .isInstanceOf(CsmSchemaBootstrapService.InvalidInstCodeException.class)
                .hasMessageContaining("hsop_0001");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "hsop_0001",   // 실제 사고 값
            "cohs",        // 소문자
            "Cohs",        // 혼합
            "COHS_1",      // 언더스코어
            "COHS1",       // 숫자
            "C",           // 너무 짧다
            "ABCDEFGHIJK", // 11자 — 너무 길다
            "CO HS",       // 공백
            "CO-HS",       // 하이픈
            "CORE",        // 대문자 CORE 는 core 예약어가 아니다
    })
    void 허용하지_않는_형식은_거부한다(String code) {
        assertThatThrownBy(() -> CsmSchemaBootstrapService.requireValidInstCode(code))
                .isInstanceOf(CsmSchemaBootstrapService.InvalidInstCodeException.class);
    }

    @Test
    void 빈_값과_null_을_거부한다() {
        for (String bad : new String[] { null, "", "   " }) {
            assertThatThrownBy(() -> CsmSchemaBootstrapService.requireValidInstCode(bad))
                    .isInstanceOf(CsmSchemaBootstrapService.InvalidInstCodeException.class);
        }
    }

    /** 거부 메시지가 <b>무엇을 넣어야 하는지</b> 알려줘야 한다. */
    @Test
    void 거부_메시지에_허용_형식이_들어간다() {
        assertThatThrownBy(() -> CsmSchemaBootstrapService.requireValidInstCode("hsop_0001"))
                .hasMessageContaining("대문자 영문 2~10자")
                .hasMessageContaining("COHS")
                .hasMessageContaining("등록 후 변경할 수 없습니다");
    }

    @Test
    void 앞뒤_공백은_제거한다() {
        assertThat(CsmSchemaBootstrapService.requireValidInstCode("  COHS  ")).isEqualTo("COHS");
    }

    // ── 정규화는 검증과 다른 일이다 ──────────────────────────

    /**
     * {@code normalizeInstCode} 는 <b>기존 데이터를 읽을 때</b> 쓴다.
     * 검증({@code requireValidInstCode})은 <b>새로 들어올 때</b> 쓴다.
     * 이미 있는 소문자 코드를 읽는 경로까지 막으면 동기화가 멈춘다.
     */
    @Test
    void 정규화는_기존_값을_읽기_위한_것이라_거부하지_않는다() {
        assertThat(CsmSchemaBootstrapService.normalizeInstCode("hsop_0001")).isEqualTo("HSOP_0001");
        assertThat(CsmSchemaBootstrapService.normalizeInstCode("cohs")).isEqualTo("COHS");
        assertThat(CsmSchemaBootstrapService.normalizeInstCode("CORE")).isEqualTo("core");
        assertThat(CsmSchemaBootstrapService.normalizeInstCode("  Core  ")).isEqualTo("core");
    }

    @Test
    void 정규화는_빈_값에_null_을_돌려준다() {
        assertThat(CsmSchemaBootstrapService.normalizeInstCode(null)).isNull();
        assertThat(CsmSchemaBootstrapService.normalizeInstCode("")).isNull();
        assertThat(CsmSchemaBootstrapService.normalizeInstCode("   ")).isNull();
    }

    // ⭐ 플랫폼 벡터 대조는 InstCodeVectorsTest 로 옮겼다 (CSM-5).
    //    I01~I13 을 손으로 옮겨 뒀었는데, 이제 벡터 **파일을 직접 읽어** 돌린다.
    //    여기 남은 것은 csm 고유 규칙(requireValidInstCode)이다 — 벡터가 고정하지 않는다.
}
