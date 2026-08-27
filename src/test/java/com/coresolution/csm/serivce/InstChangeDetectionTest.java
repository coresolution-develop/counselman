package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.csm.serivce.CsmSchemaBootstrapService.InstChange;

/**
 * 기관 변경 감지 (CSM-6).
 *
 * <p>⭐ <b>"변경 없으면 통지 없음" 이 이 테스트의 핵심이다.</b>
 * {@code refreshFromPlatform} 은 <b>10분마다</b> 돈다. 매번 통지하면
 * 기관 6곳 × 6회/시간 = 하루 864건이 나가고, <b>진짜 변경이 그 안에 묻힌다.</b>
 *
 * <p>DB 없이 {@code JdbcTemplate} 을 목으로 세워 <b>판정 로직만</b> 본다.
 * 적재·전송은 {@code InstSyncIntegrationTest} 가 실제 DB 로 본다.
 */
class InstChangeDetectionTest {

    private JdbcTemplate jdbc;
    private CsmSchemaBootstrapService service;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        service = new CsmSchemaBootstrapService(jdbc, null, null, null, null);
    }

    /** {@code inst_data_cs} 의 현재 행을 흉내 낸다. 빈 리스트면 신규 기관이다. */
    private void existing(String name, String useYn) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (name != null || useYn != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_col_02", name);
            row.put("id_col_04", useYn);
            rows.add(row);
        }
        Mockito.when(jdbc.queryForList(Mockito.contains("SELECT id_col_02, id_col_04"),
                Mockito.any(Object[].class))).thenReturn(rows);
    }

    private InstChange upsert(String code, String name, String useYn) {
        return (InstChange) ReflectionTestUtils.invokeMethod(
                service, "upsertCoreInstitution", code, name, useYn);
    }

    // ── ⭐ 변경 없으면 통지 없음 ───────────────────────────────

    /**
     * ⭐ 값이 같으면 {@code null} 이다 — 통지하지 않는다.
     *
     * <p>이게 없으면 10분마다 전 기관이 통지된다.
     */
    @Test
    void 바뀐_것이_없으면_통지하지_않는다() {
        existing("코어병원", "y");

        assertThat(upsert("COHS", "코어병원", "y"))
                .as("10분마다 6건씩 나가면 진짜 변경이 묻힌다")
                .isNull();
    }

    /** UPDATE 는 그대로 실행된다 — 통지를 안 할 뿐 반영은 한다. */
    @Test
    void 변경이_없어도_반영은_한다() {
        existing("코어병원", "y");

        upsert("COHS", "코어병원", "y");

        Mockito.verify(jdbc).update(Mockito.contains("UPDATE csm.inst_data_cs"),
                Mockito.any(Object[].class));
    }

    // ── 세 가지 변경 ──────────────────────────────────────────

    @Test
    void 행이_없으면_신규다() {
        existing(null, null);

        InstChange c = upsert("NEWH", "신규병원", "y");

        assertThat(c).isNotNull();
        assertThat(c.changeType()).isEqualTo("CREATED");
        assertThat(c.instCode()).isEqualTo("NEWH");
    }

    /** ⭐ 예전에는 <b>영영 반영되지 않던</b> 변경이다. */
    @Test
    void use_yn_이_바뀌면_감지한다() {
        existing("코어병원", "y");

        InstChange c = upsert("COHS", "코어병원", "n");

        assertThat(c).isNotNull();
        assertThat(c.changeType()).isEqualTo("USE_YN_CHANGED");
        assertThat(c.useYn()).isEqualTo("n");
    }

    @Test
    void 이름이_바뀌면_감지한다() {
        existing("코어병원", "y");

        InstChange c = upsert("COHS", "코어의료재단", "y");

        assertThat(c).isNotNull();
        assertThat(c.changeType()).isEqualTo("RENAMED");
        assertThat(c.instName()).isEqualTo("코어의료재단");
    }

    /**
     * 둘이 같이 바뀌면 <b>{@code USE_YN_CHANGED} 를 먼저</b> 알린다.
     *
     * <p>통지는 한 건인데 이름 변경으로 보내면 <b>운영에 더 중요한 사실이 가려진다.</b>
     * 이름은 다음 주기에 {@code RENAMED} 로 따로 나간다.
     */
    @Test
    void 둘이_같이_바뀌면_use_yn_을_먼저_알린다() {
        existing("코어병원", "y");

        assertThat(upsert("COHS", "코어의료재단", "n").changeType())
                .isEqualTo("USE_YN_CHANGED");
    }

    // ── 경계 ──────────────────────────────────────────────────

    /** 기존 행의 값이 {@code null} 이어도(예전 데이터) 터지지 않고 변경으로 본다. */
    @Test
    void 기존_값이_null_이면_변경으로_본다() {
        existing(null, "y");

        assertThat(upsert("COHS", "코어병원", "y").changeType()).isEqualTo("RENAMED");
    }

    /**
     * {@code use_yn} 대소문자가 다르면 <b>변경으로 본다.</b>
     *
     * <p>{@code toCounselmanYn} 이 표기를 고정하므로 정상 흐름에서는 안 나온다.
     * 나온다면 그건 <b>다른 경로가 값을 넣었다는 신호</b>다 — 통지되는 편이 낫다.
     */
    @Test
    void use_yn_표기가_다르면_변경으로_본다() {
        existing("코어병원", "Y");

        assertThat(upsert("COHS", "코어병원", "y"))
                .as("표기가 흔들리는 것 자체가 알아야 할 사실이다")
                .isNotNull();
    }

    /** 통지 내용이 <b>지금 반영한 값</b>과 같아야 한다 — 따로 조회하면 갈릴 수 있다. */
    @Test
    void 통지_내용이_반영한_값과_같다() {
        existing("옛이름", "y");

        InstChange c = upsert("COHS", "새이름", "n");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbc).update(Mockito.contains("UPDATE csm.inst_data_cs"), args.capture());

        assertThat(args.getValue()[0]).as("DB 에 쓴 이름").isEqualTo("새이름");
        assertThat(c.instName()).as("통지한 이름").isEqualTo("새이름");
        assertThat(args.getValue()[2]).as("DB 에 쓴 use_yn").isEqualTo("n");
        assertThat(c.useYn()).as("통지한 use_yn").isEqualTo("n");
    }
}
