package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import com.coresolution.csm.vo.RoomBoardRoomConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for room-config helper logic introduced with operational available
 * beds (가용병상수) and room gender (성별).
 */
@ExtendWith(MockitoExtension.class)
class RoomBoardServiceRoomConfigTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RoomBoardService service() {
        return new RoomBoardService(jdbcTemplate);
    }

    @Test
    void effectiveAvailableBeds_defaultsToLicensed_whenUnsetOrNonPositive() {
        RoomBoardService service = service();
        assertEquals(6, service.effectiveAvailableBeds(null, 6));
        assertEquals(6, service.effectiveAvailableBeds(0, 6));
        assertEquals(6, service.effectiveAvailableBeds(-3, 6));
    }

    @Test
    void effectiveAvailableBeds_capsAtLicensed() {
        RoomBoardService service = service();
        assertEquals(6, service.effectiveAvailableBeds(9, 6));
        assertEquals(4, service.effectiveAvailableBeds(4, 6));
    }

    @Test
    void normalizeRoomGender_mapsKnownTokens() {
        RoomBoardService service = service();
        assertEquals("남", service.normalizeRoomGender("남"));
        assertEquals("남", service.normalizeRoomGender("남성"));
        assertEquals("남", service.normalizeRoomGender("m"));
        assertEquals("여", service.normalizeRoomGender("여성"));
        assertEquals("여", service.normalizeRoomGender("F"));
        assertEquals("혼용", service.normalizeRoomGender("공용"));
        assertEquals("혼용", service.normalizeRoomGender("mixed"));
    }

    @Test
    void normalizeRoomGender_returnsBlankForUnknownOrEmpty() {
        RoomBoardService service = service();
        assertEquals("", service.normalizeRoomGender(null));
        assertEquals("", service.normalizeRoomGender(""));
        assertEquals("", service.normalizeRoomGender("기타"));
    }

    @Test
    void admissionDays_countsAdmissionDayAsDayOne() {
        RoomBoardService service = service();
        LocalDate base = LocalDate.of(2026, 6, 16);
        assertEquals(1, service.admissionDays("2026-06-16", base));
        assertEquals(7, service.admissionDays("2026-06-10", base));
    }

    @Test
    void admissionDays_isNullForBlankOrFutureDate() {
        RoomBoardService service = service();
        LocalDate base = LocalDate.of(2026, 6, 16);
        assertNull(service.admissionDays(null, base));
        assertNull(service.admissionDays("", base));
        assertNull(service.admissionDays("2026-06-20", base), "future admission date is not a valid stay length");
    }

    @Test
    void genderLabel_mapsToKoreanLabel() {
        RoomBoardService service = service();
        assertEquals("남", service.genderLabel("M"));
        assertEquals("여", service.genderLabel("F"));
        assertEquals("남", service.genderLabel("남"));
        assertEquals("여", service.genderLabel("여"));
        assertEquals("", service.genderLabel(""));
    }

    // ── 병실 기준정보 겹침 감지(붙여넣기 저장 차단) ──────────────────────────

    private RoomBoardService.RoomConfigPeriod period(String ward, String room, String start, String end) {
        return new RoomBoardService.RoomConfigPeriod(ward, room, LocalDate.parse(start), LocalDate.parse(end));
    }

    private RoomBoardRoomConfig parsedRow(String ward, String room, String start, String end) {
        RoomBoardRoomConfig c = new RoomBoardRoomConfig();
        c.setWardName(ward);
        c.setRoomName(room);
        c.setStartDate(start);
        c.setEndDate(end);
        return c;
    }

    @Test
    void detectConflicts_flags_whenNewPeriodOverlapsExistingActive() {
        RoomBoardService service = service();
        // 기존: 102호 2023-06-27~9999-12-31 (열린 활성), 새로: 102호 2026-06-16~9999-12-31 → 겹침
        var conflicts = service.detectRoomConfigConflicts(
                List.of(period("1병동", "102호", "2023-06-27", "9999-12-31")),
                List.of(parsedRow("1병동", "102호", "2026-06-16", "9999-12-31")));
        assertEquals(1, conflicts.size());
    }

    @Test
    void detectConflicts_allows_exactSamePeriod_asUpdate() {
        RoomBoardService service = service();
        var conflicts = service.detectRoomConfigConflicts(
                List.of(period("1병동", "102호", "2023-06-27", "9999-12-31")),
                List.of(parsedRow("1병동", "102호", "2023-06-27", "9999-12-31")));
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_allows_cleanHandoff_nonOverlapping() {
        RoomBoardService service = service();
        // 101호: 옛 기간 ~2024-06-30 마감 후 2024-07-01 새로 개시 → 겹치지 않음
        var conflicts = service.detectRoomConfigConflicts(
                List.of(period("1병동", "101호", "2019-11-30", "2024-06-30")),
                List.of(parsedRow("1병동", "101호", "2024-07-01", "9999-12-31")));
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_flags_overlapWithinSamePastedBatch() {
        RoomBoardService service = service();
        // 붙여넣기 안에서 105호가 겹치는 두 기간으로 들어옴
        var conflicts = service.detectRoomConfigConflicts(
                List.of(),
                List.of(
                        parsedRow("1병동", "105호", "2024-07-01", "9999-12-31"),
                        parsedRow("1병동", "105호", "2025-01-01", "9999-12-31")));
        assertEquals(1, conflicts.size());
    }
}
