package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

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
}
