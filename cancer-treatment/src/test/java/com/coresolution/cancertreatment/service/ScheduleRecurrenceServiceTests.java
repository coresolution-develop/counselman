package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.cancertreatment.model.ScheduleRecurrenceRequest;
import com.coresolution.cancertreatment.service.ScheduleRecurrenceService.Result;

@SpringBootTest
class ScheduleRecurrenceServiceTests {

    @Autowired
    private ScheduleRecurrenceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ScheduleRecurrenceRequest baseRequest() {
        ScheduleRecurrenceRequest req = new ScheduleRecurrenceRequest();
        req.setPatientName("홍길동");
        req.setTreatmentName("고주파");
        req.setStartDate("2026-06-01");
        req.setStartTime("10:00");
        return req;
    }

    @Test
    void oneOffCreatesSingleRowWithoutRule() {
        ScheduleRecurrenceRequest req = baseRequest();
        req.setRepeat(false);

        Result result = service.createSchedules("core", "tester", req);

        assertThat(result.recurrenceId()).isNull();
        assertThat(result.createdCount()).isEqualTo(1);
    }

    @Test
    void repeatMaterializesExactlyNRows() {
        ScheduleRecurrenceRequest req = baseRequest();
        req.setRepeat(true);
        req.setWeekdayMask(0b1111111); // 매일
        req.setOccurrenceCount(6);

        Result result = service.createSchedules("core", "tester", req);

        assertThat(result.recurrenceId()).isNotNull();
        assertThat(result.createdCount()).isEqualTo(6);
        // 매일 매칭이므로 마지막 일정 = 시작일 + 5일
        assertThat(result.lastDate()).isEqualTo(LocalDate.parse("2026-06-01").plusDays(5).toString());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ct_treatment_schedule WHERE recurrence_id = ?",
                Integer.class, result.recurrenceId());
        assertThat(rows).isEqualTo(6);
    }

    @Test
    void repeatRespectsWeekdayMask() {
        ScheduleRecurrenceRequest req = baseRequest();
        LocalDate start = LocalDate.parse("2026-06-01");
        int onlyStartWeekday = 1 << (start.getDayOfWeek().getValue() - 1); // 시작일 요일만
        req.setRepeat(true);
        req.setWeekdayMask(onlyStartWeekday);
        req.setOccurrenceCount(3);

        Result result = service.createSchedules("core", "tester", req);

        // 같은 요일만 → 7일 간격 3회 → 마지막 = 시작 + 14일
        assertThat(result.lastDate()).isEqualTo(start.plusDays(14).toString());
        assertThat(result.createdCount()).isEqualTo(3);
    }

    @Test
    void rejectsZeroWeekdayMask() {
        ScheduleRecurrenceRequest req = baseRequest();
        req.setRepeat(true);
        req.setWeekdayMask(0);
        req.setOccurrenceCount(3);
        assertThatThrownBy(() -> service.createSchedules("core", "tester", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("반복 요일");
    }

    @Test
    void rejectsNonPositiveCount() {
        ScheduleRecurrenceRequest req = baseRequest();
        req.setRepeat(true);
        req.setWeekdayMask(0b0000001);
        req.setOccurrenceCount(0);
        assertThatThrownBy(() -> service.createSchedules("core", "tester", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void rejectsCountOverCap() {
        ScheduleRecurrenceRequest req = baseRequest();
        req.setRepeat(true);
        req.setWeekdayMask(0b1111111);
        req.setOccurrenceCount(ScheduleRecurrenceService.MAX_OCCURRENCE_COUNT + 1);
        assertThatThrownBy(() -> service.createSchedules("core", "tester", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초과");
    }

    @Test
    void warnsWhenLastOccurrenceExceedsPrescriptionBoundary() {
        ScheduleRecurrenceRequest req = baseRequest();
        req.setRepeat(true);
        req.setWeekdayMask(0b1111111); // 매일 → 30일이면 30회가 30일에 걸침
        req.setOccurrenceCount(30);
        req.setPrescriptionWeeks(1); // 경계 = 시작 + 6일 → 30회는 훨씬 초과

        Result result = service.createSchedules("core", "tester", req);

        assertThat(result.createdCount()).isEqualTo(30); // count 우선 — 잘리지 않음
        assertThat(result.warning()).isNotNull();
        assertThat(result.warning()).contains("예상 치료기간");
    }
}
