package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.coresolution.csm.vo.RoomBoardRoomConfig;

/**
 * Regression tests for the room-board reset/delete operations introduced to
 * recover from mistaken paste/import. Verifies SQL targeting, cascade order,
 * discharge-notice preservation, per-row history logging, and inst guarding.
 */
@ExtendWith(MockitoExtension.class)
class RoomBoardServiceResetTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RoomBoardService service() {
        return new RoomBoardService(jdbcTemplate);
    }

    // ── deleteSnapshot ─────────────────────────────────────────────────────

    @Test
    void deleteSnapshot_deletesPatientThenSnapshot_preservesDischargeNotice() {
        RoomBoardService service = service();
        when(jdbcTemplate.update(contains("room_board_patient_test"), eq(5L))).thenReturn(3);
        when(jdbcTemplate.update(contains("room_board_snapshot_test"), eq(5L))).thenReturn(1);

        Map<String, Integer> result = service.deleteSnapshot("test", 5L, "admin");

        assertEquals(3, result.get("patients"));
        assertEquals(1, result.get("snapshots"));
        InOrder ordered = inOrder(jdbcTemplate);
        ordered.verify(jdbcTemplate).update(contains("room_board_patient_test"), eq(5L));
        ordered.verify(jdbcTemplate).update(contains("room_board_snapshot_test"), eq(5L));
        // discharge notices must be preserved
        verify(jdbcTemplate, never()).update(contains("room_board_discharge_notice"), anyLong());
    }

    @Test
    void deleteSnapshot_rejectsNonPositiveId() {
        RoomBoardService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.deleteSnapshot("test", 0L, "admin"));
        verify(jdbcTemplate, never()).update(contains("room_board_patient_test"), anyLong());
        verify(jdbcTemplate, never()).update(contains("room_board_snapshot_test"), anyLong());
    }

    @Test
    void deleteSnapshot_rejectsInvalidInst() {
        RoomBoardService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.deleteSnapshot("a;DROP", 1L, "admin"));
        verify(jdbcTemplate, never()).update(contains("room_board_patient"), anyLong());
    }

    // ── resetAllSnapshots ──────────────────────────────────────────────────

    @Test
    void resetAllSnapshots_deletesAllPatientsAndSnapshots_preservesDischargeNotice() {
        RoomBoardService service = service();
        when(jdbcTemplate.update("DELETE FROM csm.room_board_patient_test")).thenReturn(10);
        when(jdbcTemplate.update("DELETE FROM csm.room_board_snapshot_test")).thenReturn(3);

        Map<String, Integer> result = service.resetAllSnapshots("test", "admin");

        assertEquals(10, result.get("patients"));
        assertEquals(3, result.get("snapshots"));
        verify(jdbcTemplate, never()).update(contains("room_board_discharge_notice"));
    }

    // ── resetRoomConfigsByStartDate ────────────────────────────────────────

    @Test
    void resetRoomConfigsByStartDate_deletesBatchAndLogsPerRow() {
        RoomBoardService service = service();
        when(jdbcTemplate.<RoomBoardRoomConfig>query(contains("WHERE start_date = ?"), any(RowMapper.class), any()))
                .thenReturn(List.of(roomConfig(1L), roomConfig(2L)));
        when(jdbcTemplate.update(startsWith("DELETE FROM csm.room_board_room_master_test WHERE start_date"),
                any(LocalDate.class))).thenReturn(2);

        int deleted = service.resetRoomConfigsByStartDate("test", "2026-06-01", "admin");

        assertEquals(2, deleted);
        // one DELETE history row per deleted config (logRoomConfigChange → 18 bind params)
        verify(jdbcTemplate, times(2)).update(
                contains("room_board_room_master_history_test"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void resetRoomConfigsByStartDate_noopWhenEmpty() {
        RoomBoardService service = service();
        when(jdbcTemplate.<RoomBoardRoomConfig>query(contains("WHERE start_date = ?"), any(RowMapper.class), any()))
                .thenReturn(List.of());

        int deleted = service.resetRoomConfigsByStartDate("test", "2026-06-01", "admin");

        assertEquals(0, deleted);
        verify(jdbcTemplate, never()).update(
                startsWith("DELETE FROM csm.room_board_room_master_test WHERE start_date"), any(LocalDate.class));
    }

    @Test
    void resetRoomConfigsByStartDate_rejectsBlankDate() {
        RoomBoardService service = service();
        assertThrows(IllegalArgumentException.class,
                () -> service.resetRoomConfigsByStartDate("test", "", "admin"));
    }

    // ── deleteRoomConfigHistory ────────────────────────────────────────────

    @Test
    void deleteRoomConfigHistory_clearsHistoryTableOnly() {
        RoomBoardService service = service();
        when(jdbcTemplate.update("DELETE FROM csm.room_board_room_master_history_test")).thenReturn(7);

        int deleted = service.deleteRoomConfigHistory("test");

        assertEquals(7, deleted);
        verify(jdbcTemplate, never()).update("DELETE FROM csm.room_board_room_master_test");
    }

    private RoomBoardRoomConfig roomConfig(long id) {
        RoomBoardRoomConfig c = new RoomBoardRoomConfig();
        c.setId(id);
        c.setWardName("3W");
        c.setRoomName("30" + id);
        c.setStartDate("2026-06-01");
        c.setEndDate("9999-12-31");
        c.setLicensedBeds(6);
        c.setStatusWalk("N");
        c.setStatusDiaper("N");
        c.setStatusOxygen("N");
        c.setStatusSuction("N");
        c.setUseYn("Y");
        return c;
    }
}
