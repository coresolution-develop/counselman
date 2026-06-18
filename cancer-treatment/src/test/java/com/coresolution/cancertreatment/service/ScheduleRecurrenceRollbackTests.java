package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.cancertreatment.model.ScheduleRecurrenceRequest;
import com.coresolution.cancertreatment.repository.TreatmentScheduleRepository;

/**
 * Verifies recurring registration is atomic: when an occurrence insert fails mid-way,
 * the already-inserted recurrence rule is rolled back too (no partial save).
 */
@SpringBootTest
class ScheduleRecurrenceRollbackTests {

    @Autowired
    private ScheduleRecurrenceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TreatmentScheduleRepository scheduleRepository;

    @Test
    void rollsBackRuleWhenOccurrenceInsertFails() {
        // occurrence 삽입이 항상 실패 → 규칙은 먼저 INSERT된 뒤 예외 발생
        given(scheduleRepository.createOccurrence(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("boom"));

        ScheduleRecurrenceRequest req = new ScheduleRecurrenceRequest();
        req.setPatientName("홍길동");
        req.setTreatmentName("고주파");
        req.setStartDate("2026-06-01");
        req.setStartTime("10:00");
        req.setRepeat(true);
        req.setWeekdayMask(0b1111111);
        req.setOccurrenceCount(3);

        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ct_schedule_recurrence WHERE inst_code = ?", Integer.class, "core");

        assertThatThrownBy(() -> service.createSchedules("core", "tester", req))
                .isInstanceOf(RuntimeException.class);

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ct_schedule_recurrence WHERE inst_code = ?", Integer.class, "core");

        // 규칙 행이 롤백되어 증가하지 않아야 함 (부분 저장 없음)
        assertThat(after).isEqualTo(before);
    }
}
