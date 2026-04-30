package com.coresolution.csm.serivce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.coresolution.csm.vo.RoomBoardSnapshot;

@ExtendWith(MockitoExtension.class)
class RoomBoardServiceAdmissionReservationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void confirmAdmission_savesCurrentRoomAndAddsPatientToLatestSnapshot() {
        RoomBoardService service = new RoomBoardService(jdbcTemplate);
        RoomBoardSnapshot snapshot = new RoomBoardSnapshot();
        snapshot.setId(7L);
        snapshot.setSnapshotDate(LocalDate.now().toString());

        when(jdbcTemplate.queryForList(contains("FROM csm.counsel_data_FALH"), eq(100L)))
                .thenReturn(List.of(Map.of(
                        "cs_idx", 100L,
                        "patient_name_hex", "BAD",
                        "gender", "여",
                        "planned_date", "2026-04-30",
                        "room_name", "101호")));
        when(jdbcTemplate.query(contains("FROM csm.room_board_snapshot_FALH"), any(RowMapper.class)))
                .thenReturn(List.of(snapshot));
        when(jdbcTemplate.queryForObject(contains("FROM csm.room_board_patient_FALH"), eq(Integer.class),
                eq(7L), eq("101호"), eq("입원완료환자")))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(contains("FROM csm.room_board_room_master_FALH"), eq(String.class),
                eq("101호"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of("1병동"));
        when(jdbcTemplate.update(
                contains("SET cs_col_21 = ?, cs_col_38 = ?, updated_at = NOW() WHERE cs_idx = ?"),
                eq("2026-04-30"),
                eq("101호"),
                eq(100L)))
                .thenReturn(1);

        service.confirmAdmission("FALH", 100L, "2026-04-30", "101호");

        verify(jdbcTemplate).update(
                contains("SET cs_col_21 = ?, cs_col_38 = ?, updated_at = NOW() WHERE cs_idx = ?"),
                eq("2026-04-30"),
                eq("101호"),
                eq(100L));
        verify(jdbcTemplate).update(
                contains("SET cs_col_19 = '입원완료', updated_at = NOW() WHERE cs_idx = ?"),
                eq(100L));
        verify(jdbcTemplate).update(
                contains("INSERT INTO csm.room_board_patient_FALH"),
                eq(7L),
                eq("1병동"),
                eq("101호"),
                eq("입원완료환자"),
                eq("F"),
                eq("2026-04-30"),
                eq("입원완료"),
                eq("입원예약관리에서 입원완료 처리"),
                eq("admission-reservation:100"));
    }
}
