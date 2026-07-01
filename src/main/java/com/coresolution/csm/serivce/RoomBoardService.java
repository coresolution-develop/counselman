package com.coresolution.csm.serivce;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.AdmissionReservationItem;
import com.coresolution.csm.vo.RoomBoardImportResult;
import com.coresolution.csm.vo.RoomBoardImportRow;
import com.coresolution.csm.vo.RoomBoardRoomConfig;
import com.coresolution.csm.vo.RoomBoardRoomConfigHistory;
import com.coresolution.csm.vo.RoomBoardRoomConfigPasteResult;
import com.coresolution.csm.vo.RoomBoardRoomView;
import com.coresolution.csm.vo.RoomBoardSnapshot;
import com.coresolution.csm.vo.RoomBoardView;
import com.coresolution.csm.vo.RoomBoardWardView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomBoardService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final JdbcTemplate jdbcTemplate;

    @Value("${login.aes-key}")
    private String aesKey;

    private String sanitizeInst(String inst) {
        if (inst == null) {
            throw new IllegalArgumentException("inst is null");
        }
        String normalized = inst.trim();
        if (!normalized.matches("[A-Za-z0-9_]{2,20}")) {
            throw new IllegalArgumentException("Invalid inst: " + inst);
        }
        return normalized;
    }

    public void ensureTables(String inst) {
        String safe = sanitizeInst(inst);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.room_board_room_master_%s (
                    rbm_id bigint auto_increment primary key,
                    ward_name varchar(50) not null,
                    room_name varchar(50) not null,
                    start_date date not null,
                    end_date date not null,
                    licensed_beds int not null default 0,
                    available_beds int default null,
                    room_gender varchar(10) default null,
                    care_type varchar(100) default null,
                    status_walk char(1) not null default 'N',
                    status_diaper char(1) not null default 'N',
                    status_oxygen char(1) not null default 'N',
                    status_suction char(1) not null default 'N',
                    nursing_cost varchar(100) default null,
                    note varchar(500) default null,
                    use_yn char(1) not null default 'Y',
                    created_at timestamp default current_timestamp,
                    created_by varchar(100) default null,
                    updated_at timestamp default current_timestamp on update current_timestamp,
                    updated_by varchar(100) default null
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(safe));
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.room_board_snapshot_%s (
                    rbs_id bigint auto_increment primary key,
                    source_type varchar(30) not null,
                    snapshot_date date not null,
                    snapshot_time varchar(5) default null,
                    raw_text longtext,
                    uploaded_by varchar(100) default null,
                    uploaded_at timestamp default current_timestamp,
                    parse_status varchar(20) not null default 'SUCCESS',
                    parse_message varchar(1000) default null
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(safe));
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.room_board_patient_%s (
                    rbp_id bigint auto_increment primary key,
                    rbs_id bigint not null,
                    ward_name varchar(50) default null,
                    room_name varchar(50) not null,
                    patient_no varchar(100) default null,
                    patient_name varchar(100) default null,
                    gender varchar(10) default null,
                    age varchar(20) default null,
                    admission_date varchar(20) default null,
                    doctor_name varchar(100) default null,
                    patient_type varchar(50) default null,
                    disease_name varchar(200) default null,
                    disease_code varchar(100) default null,
                    phone_patient varchar(50) default null,
                    phone_guardian varchar(50) default null,
                    memo varchar(1000) default null,
                    raw_row longtext,
                    created_at timestamp default current_timestamp,
                    key idx_snapshot (rbs_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(safe));
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.room_board_discharge_notice_%s (
                    rbdn_id bigint auto_increment primary key,
                    rbp_id bigint default null,
                    snapshot_id bigint default null,
                    ward_name varchar(50) default null,
                    room_name varchar(50) not null,
                    patient_no varchar(100) default null,
                    patient_name varchar(100) not null,
                    discharge_date date not null,
                    discharge_time varchar(10) not null default 'AM',
                    status varchar(20) not null default 'PLANNED',
                    note varchar(1000) default null,
                    created_at timestamp default current_timestamp,
                    created_by varchar(100) default null,
                    updated_at timestamp default current_timestamp on update current_timestamp,
                    updated_by varchar(100) default null,
                    key idx_discharge_date (discharge_date, status),
                    key idx_room_date (room_name, discharge_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(safe));
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS csm.room_board_room_master_history_%s (
                    rbmh_id bigint auto_increment primary key,
                    rbm_id bigint default null,
                    action varchar(10) not null,
                    ward_name varchar(50) default null,
                    room_name varchar(50) default null,
                    start_date date default null,
                    end_date date default null,
                    licensed_beds int default null,
                    available_beds int default null,
                    room_gender varchar(10) default null,
                    care_type varchar(100) default null,
                    status_walk char(1) default null,
                    status_diaper char(1) default null,
                    status_oxygen char(1) default null,
                    status_suction char(1) default null,
                    nursing_cost varchar(100) default null,
                    note varchar(500) default null,
                    use_yn char(1) default null,
                    changed_by varchar(100) default null,
                    changed_at timestamp default current_timestamp,
                    key idx_rbm (rbm_id),
                    key idx_changed (changed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.formatted(safe));
        ensureRoomMasterColumns(safe);
        ensurePatientColumns(safe);
    }

    /**
     * Adds columns introduced after the initial room_master schema to existing
     * per-institution tables (CREATE TABLE IF NOT EXISTS does not alter them).
     */
    private void ensureRoomMasterColumns(String safe) {
        String table = "room_board_room_master_" + safe;
        addColumnIfMissing(table, "available_beds", "available_beds int default null AFTER licensed_beds");
        addColumnIfMissing(table, "room_gender", "room_gender varchar(10) default null AFTER available_beds");
    }

    private void ensurePatientColumns(String safe) {
        String table = "room_board_patient_" + safe;
        addColumnIfMissing(table, "bed_no", "bed_no int default null AFTER room_name");
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tableName, columnName);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE csm." + tableName + " ADD COLUMN " + columnDefinition);
                log.info("[room-board] added column {} to {}", columnName, tableName);
            }
        } catch (Exception e) {
            log.warn("[room-board] column migration skipped {}.{}: {}", tableName, columnName, e.toString());
        }
    }

    public List<RoomBoardRoomConfig> getRoomConfigs(String inst) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbm_id, ward_name, room_name, DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date, licensed_beds, available_beds, room_gender, care_type,
                       status_walk, status_diaper, status_oxygen, status_suction,
                       nursing_cost, note, use_yn,
                       DATE_FORMAT(created_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS created_at,
                       created_by,
                       DATE_FORMAT(updated_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS updated_at,
                       updated_by
                  FROM csm.room_board_room_master_%s
                 ORDER BY start_date DESC, rbm_id DESC
                """.formatted(safe);
        List<RoomBoardRoomConfig> items = jdbcTemplate.query(sql, (rs, rowNum) -> mapRoomConfig(rs));
        // 관리화면 표는 병동 → 호실 순(자연수 정렬)으로 노출한다. 동일 병실은 최근 개시일 우선.
        items.sort(Comparator.comparing(RoomBoardRoomConfig::getWardName, this::compareWardName)
                .thenComparing(RoomBoardRoomConfig::getRoomName, this::compareRoomName)
                .thenComparing(RoomBoardRoomConfig::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    public RoomBoardRoomConfig getRoomConfig(String inst, long id) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbm_id, ward_name, room_name, DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date, licensed_beds, available_beds, room_gender, care_type,
                       status_walk, status_diaper, status_oxygen, status_suction,
                       nursing_cost, note, use_yn,
                       DATE_FORMAT(created_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS created_at,
                       created_by,
                       DATE_FORMAT(updated_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS updated_at,
                       updated_by
                  FROM csm.room_board_room_master_%s
                 WHERE rbm_id = ?
                """.formatted(safe);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRoomConfig(rs), id)
                .stream().findFirst().orElse(null);
    }

    private RoomBoardRoomConfig mapRoomConfig(ResultSet rs) throws SQLException {
        RoomBoardRoomConfig item = new RoomBoardRoomConfig();
        item.setId(rs.getLong("rbm_id"));
        item.setWardName(rs.getString("ward_name"));
        item.setRoomName(rs.getString("room_name"));
        item.setStartDate(rs.getString("start_date"));
        item.setEndDate(rs.getString("end_date"));
        item.setLicensedBeds(rs.getInt("licensed_beds"));
        int availableBeds = rs.getInt("available_beds");
        item.setAvailableBeds(rs.wasNull() ? null : availableBeds);
        item.setRoomGender(rs.getString("room_gender"));
        item.setCareType(rs.getString("care_type"));
        item.setStatusWalk(rs.getString("status_walk"));
        item.setStatusDiaper(rs.getString("status_diaper"));
        item.setStatusOxygen(rs.getString("status_oxygen"));
        item.setStatusSuction(rs.getString("status_suction"));
        item.setNursingCost(rs.getString("nursing_cost"));
        item.setNote(rs.getString("note"));
        item.setUseYn(rs.getString("use_yn"));
        item.setCreatedAt(rs.getString("created_at"));
        item.setCreatedBy(rs.getString("created_by"));
        item.setUpdatedAt(rs.getString("updated_at"));
        item.setUpdatedBy(rs.getString("updated_by"));
        return item;
    }

    @Transactional
    public RoomBoardRoomConfig saveRoomConfig(String inst, RoomBoardRoomConfig config, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        long id = config.getId() == null ? 0L : config.getId();
        PreparedRoomConfig prepared = prepareRoomConfig(config);
        String action;
        if (id > 0) {
            updateRoomConfig(safe, id, prepared, username);
            action = "UPDATE";
        } else {
            id = insertRoomConfig(safe, prepared, username);
            action = "CREATE";
        }
        RoomBoardRoomConfig saved = getRoomConfig(safe, id);
        logRoomConfigChange(safe, action, saved, username);
        return saved;
    }

    @Transactional
    public void deleteRoomConfig(String inst, long id, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardRoomConfig existing = getRoomConfig(safe, id);
        jdbcTemplate.update("DELETE FROM csm.room_board_room_master_" + safe + " WHERE rbm_id = ?", id);
        if (existing != null) {
            logRoomConfigChange(safe, "DELETE", existing, username);
        }
    }

    /**
     * Deletes a single uploaded snapshot and its patient rows. Discharge notices
     * are intentionally preserved (they carry their own patient columns and live
     * on the discharge-notice screen independently of the source snapshot).
     */
    @Transactional
    public Map<String, Integer> deleteSnapshot(String inst, long snapshotId, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        if (snapshotId <= 0) {
            throw new IllegalArgumentException("삭제할 업로드 이력을 확인해 주세요.");
        }
        int patients = jdbcTemplate.update(
                "DELETE FROM csm.room_board_patient_" + safe + " WHERE rbs_id = ?", snapshotId);
        int snapshots = jdbcTemplate.update(
                "DELETE FROM csm.room_board_snapshot_" + safe + " WHERE rbs_id = ?", snapshotId);
        log.info("[room-board] snapshot delete inst={}, id={}, patients={}, snapshots={}, by={}",
                safe, snapshotId, patients, snapshots, safeText(username, 100));
        return Map.of("patients", patients, "snapshots", snapshots);
    }

    /**
     * Clears all uploaded snapshots and patient rows for the institution.
     * Discharge notices are preserved (see {@link #deleteSnapshot}).
     */
    @Transactional
    public Map<String, Integer> resetAllSnapshots(String inst, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        int patients = jdbcTemplate.update("DELETE FROM csm.room_board_patient_" + safe);
        int snapshots = jdbcTemplate.update("DELETE FROM csm.room_board_snapshot_" + safe);
        log.info("[room-board] snapshot reset-all inst={}, patients={}, snapshots={}, by={}",
                safe, patients, snapshots, safeText(username, 100));
        return Map.of("patients", patients, "snapshots", snapshots);
    }

    /**
     * Deletes every room-config row sharing the given start date (one pasted
     * batch) and records a DELETE history row per deleted config.
     */
    @Transactional
    public int resetRoomConfigsByStartDate(String inst, String startDate, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        LocalDate target = parseDate(startDate, null);
        if (target == null) {
            throw new IllegalArgumentException("개시일자를 확인해 주세요.");
        }
        String selectSql = "SELECT rbm_id, ward_name, room_name, DATE_FORMAT(start_date,'%Y-%m-%d') AS start_date, "
                + "DATE_FORMAT(end_date,'%Y-%m-%d') AS end_date, licensed_beds, available_beds, room_gender, care_type, "
                + "status_walk, status_diaper, status_oxygen, status_suction, nursing_cost, note, use_yn, "
                + "DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') AS created_at, created_by, "
                + "DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS updated_at, updated_by "
                + "FROM csm.room_board_room_master_" + safe + " WHERE start_date = ?";
        List<RoomBoardRoomConfig> targets = jdbcTemplate.query(selectSql, (rs, rowNum) -> mapRoomConfig(rs), target);
        if (targets.isEmpty()) {
            return 0;
        }
        int deleted = jdbcTemplate.update(
                "DELETE FROM csm.room_board_room_master_" + safe + " WHERE start_date = ?", target);
        for (RoomBoardRoomConfig config : targets) {
            logRoomConfigChange(safe, "DELETE", config, username);
        }
        log.info("[room-board] room config reset by start_date={} inst={}, deleted={}, by={}",
                target, safe, deleted, safeText(username, 100));
        return deleted;
    }

    /**
     * Deletes all room-config rows for the institution, recording a DELETE
     * history row per deleted config.
     */
    @Transactional
    public int resetAllRoomConfigs(String inst, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        List<RoomBoardRoomConfig> targets = getRoomConfigs(safe);
        if (targets.isEmpty()) {
            return 0;
        }
        int deleted = jdbcTemplate.update("DELETE FROM csm.room_board_room_master_" + safe);
        for (RoomBoardRoomConfig config : targets) {
            logRoomConfigChange(safe, "DELETE", config, username);
        }
        log.info("[room-board] room config reset-all inst={}, deleted={}, by={}",
                safe, deleted, safeText(username, 100));
        return deleted;
    }

    /**
     * Clears the room-config change history table for the institution.
     */
    @Transactional
    public int deleteRoomConfigHistory(String inst) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        int deleted = jdbcTemplate.update("DELETE FROM csm.room_board_room_master_history_" + safe);
        log.info("[room-board] room config history cleared inst={}, deleted={}", safe, deleted);
        return deleted;
    }

    public List<RoomBoardRoomConfigHistory> getRoomConfigHistory(String inst, int limit) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        int size = Math.max(1, Math.min(limit, 100));
        String sql = """
                SELECT rbmh_id, rbm_id, action, ward_name, room_name,
                       DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date,
                       licensed_beds, available_beds, room_gender, care_type,
                       status_walk, status_diaper, status_oxygen, status_suction,
                       nursing_cost, note, use_yn, changed_by,
                       DATE_FORMAT(changed_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS changed_at
                  FROM csm.room_board_room_master_history_%s
                 ORDER BY changed_at DESC, rbmh_id DESC
                 LIMIT %d
                """.formatted(safe, size);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            RoomBoardRoomConfigHistory item = new RoomBoardRoomConfigHistory();
            item.setId(rs.getLong("rbmh_id"));
            long rbmId = rs.getLong("rbm_id");
            item.setRbmId(rs.wasNull() ? null : rbmId);
            item.setAction(rs.getString("action"));
            item.setWardName(rs.getString("ward_name"));
            item.setRoomName(rs.getString("room_name"));
            item.setStartDate(rs.getString("start_date"));
            item.setEndDate(rs.getString("end_date"));
            int licensedBeds = rs.getInt("licensed_beds");
            item.setLicensedBeds(rs.wasNull() ? null : licensedBeds);
            int availableBeds = rs.getInt("available_beds");
            item.setAvailableBeds(rs.wasNull() ? null : availableBeds);
            item.setRoomGender(rs.getString("room_gender"));
            item.setCareType(rs.getString("care_type"));
            item.setStatusWalk(rs.getString("status_walk"));
            item.setStatusDiaper(rs.getString("status_diaper"));
            item.setStatusOxygen(rs.getString("status_oxygen"));
            item.setStatusSuction(rs.getString("status_suction"));
            item.setNursingCost(rs.getString("nursing_cost"));
            item.setNote(rs.getString("note"));
            item.setUseYn(rs.getString("use_yn"));
            item.setChangedBy(rs.getString("changed_by"));
            item.setChangedAt(rs.getString("changed_at"));
            return item;
        });
    }

    private void logRoomConfigChange(String inst, String action, RoomBoardRoomConfig config, String username) {
        if (config == null) {
            return;
        }
        String safe = sanitizeInst(inst);
        String sql = """
                INSERT INTO csm.room_board_room_master_history_%s
                (rbm_id, action, ward_name, room_name, start_date, end_date, licensed_beds, available_beds,
                 room_gender, care_type, status_walk, status_diaper, status_oxygen, status_suction,
                 nursing_cost, note, use_yn, changed_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe);
        jdbcTemplate.update(sql,
                config.getId(),
                action,
                config.getWardName(),
                config.getRoomName(),
                parseDate(config.getStartDate(), null),
                parseDate(config.getEndDate(), null),
                config.getLicensedBeds(),
                config.getAvailableBeds(),
                config.getRoomGender(),
                config.getCareType(),
                normalizeYn(config.getStatusWalk()),
                normalizeYn(config.getStatusDiaper()),
                normalizeYn(config.getStatusOxygen()),
                normalizeYn(config.getStatusSuction()),
                config.getNursingCost(),
                config.getNote(),
                config.getUseYn(),
                safeText(username, 100));
    }

    public RoomBoardRoomConfigPasteResult previewRoomConfigPaste(String inst, String rawText) {
        ensureTables(inst);
        RoomBoardRoomConfigPasteResult result = new RoomBoardRoomConfigPasteResult();
        List<String[]> rows = parseRawRows(rawText);
        if (rows.isEmpty()) {
            result.setMessage("붙여넣기 데이터가 없습니다.");
            return result;
        }

        List<RoomBoardRoomConfig> parsed = parseRoomConfigRows(rows);
        result.setRows(parsed);
        result.setParsedCount(parsed.size());
        result.setSkippedCount(Math.max(0, rows.size() - parsed.size()));

        List<String> conflicts = detectRoomConfigConflicts(loadRoomConfigPeriods(sanitizeInst(inst)), parsed);
        result.setConflicts(conflicts);
        if (!conflicts.isEmpty()) {
            result.setMessage("저장 불가 · 겹치는 기준정보 " + conflicts.size()
                    + "건: 이전 기준정보를 먼저 마감(종료일 지정)한 뒤 저장하세요.");
        } else {
            result.setMessage(parsed.isEmpty() ? "인식된 병실 기준정보가 없습니다." : "병실 기준정보 미리보기 생성 완료");
        }
        return result;
    }

    @Transactional
    public RoomBoardRoomConfigPasteResult saveRoomConfigPaste(String inst, String rawText, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardRoomConfigPasteResult preview = previewRoomConfigPaste(safe, rawText);
        if (preview.getParsedCount() <= 0) {
            throw new IllegalArgumentException("저장할 병실 기준정보가 없습니다.");
        }
        // 이미 활성이거나 기간이 겹치는 병실이 있으면 저장을 차단한다(중복 활성 기준정보 → 현황판 병실 미표시 예방).
        if (!preview.getConflicts().isEmpty()) {
            throw new IllegalArgumentException(
                    "겹치는 기준정보가 있어 저장할 수 없습니다 · " + String.join(" / ", preview.getConflicts())
                            + " — 이전 기준정보를 먼저 마감(종료일 지정)한 뒤 다시 저장하세요.");
        }
        for (RoomBoardRoomConfig item : preview.getRows()) {
            upsertRoomConfigByPeriod(safe, item, username);
        }
        return preview;
    }

    public List<RoomBoardSnapshot> getSnapshotHistory(String inst, int limit) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        int size = Math.max(1, Math.min(limit, 30));
        String sql = """
                SELECT rbs_id, source_type, DATE_FORMAT(snapshot_date,'%%Y-%%m-%%d') AS snapshot_date,
                       snapshot_time, uploaded_by, DATE_FORMAT(uploaded_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS uploaded_at,
                       parse_status, parse_message
                  FROM csm.room_board_snapshot_%s
                 ORDER BY snapshot_date DESC, snapshot_time DESC, rbs_id DESC
                 LIMIT %d
                """.formatted(safe, size);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            RoomBoardSnapshot item = new RoomBoardSnapshot();
            item.setId(rs.getLong("rbs_id"));
            item.setSourceType(rs.getString("source_type"));
            item.setSnapshotDate(rs.getString("snapshot_date"));
            item.setSnapshotTime(rs.getString("snapshot_time"));
            item.setUploadedBy(rs.getString("uploaded_by"));
            item.setUploadedAt(rs.getString("uploaded_at"));
            item.setParseStatus(rs.getString("parse_status"));
            item.setParseMessage(rs.getString("parse_message"));
            return item;
        });
    }

    public List<Map<String, Object>> listCurrentRoomBoardPatients(String inst) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardSnapshot snapshot = findSnapshot(safe, null);
        if (snapshot == null || snapshot.getId() == null) {
            return List.of();
        }
        String sql = """
                SELECT rbp_id, rbs_id, ward_name, room_name, patient_no, patient_name, gender, age,
                       admission_date, doctor_name, patient_type, memo
                  FROM csm.room_board_patient_%s
                 WHERE rbs_id = ?
                 ORDER BY ward_name ASC, room_name ASC, patient_name ASC
                """.formatted(safe);
        return jdbcTemplate.queryForList(sql, snapshot.getId());
    }

    public List<Map<String, Object>> listDischargeNotices(String inst, String date) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        LocalDate targetDate = parseDate(date, LocalDate.now());
        String sql = """
                SELECT rbdn_id, rbp_id, snapshot_id, ward_name, room_name, patient_no, patient_name,
                       DATE_FORMAT(discharge_date,'%%Y-%%m-%%d') AS discharge_date,
                       discharge_time, status, note,
                       DATE_FORMAT(created_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS created_at,
                       created_by,
                       DATE_FORMAT(updated_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS updated_at,
                       updated_by
                  FROM csm.room_board_discharge_notice_%s
                 WHERE discharge_date = ?
                 ORDER BY
                       CASE status WHEN 'PLANNED' THEN 1 WHEN 'COMPLETED' THEN 2 ELSE 3 END,
                       CASE discharge_time WHEN 'AM' THEN 1 WHEN 'PM' THEN 2 ELSE 3 END,
                       ward_name ASC, room_name ASC, patient_name ASC
                """.formatted(safe);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, targetDate);
        for (Map<String, Object> row : rows) {
            row.put("statusLabel", dischargeStatusLabel(Objects.toString(row.get("status"), "")));
            row.put("timeLabel", dischargeTimeLabel(Objects.toString(row.get("discharge_time"), "")));
            row.put("availabilityLabel", dischargeAvailabilityLabel(
                    Objects.toString(row.get("status"), ""),
                    Objects.toString(row.get("discharge_time"), "")));
        }
        return rows;
    }

    @Transactional
    public long saveDischargeNotice(
            String inst,
            Long rbpId,
            String dischargeDate,
            String dischargeTime,
            String status,
            String note,
            String actor) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        LocalDate date = parseDate(dischargeDate, LocalDate.now());
        String normalizedTime = normalizeDischargeTime(dischargeTime);
        String normalizedStatus = normalizeDischargeStatus(status);
        Map<String, Object> patient = findCurrentPatientForDischarge(safe, rbpId);
        if (patient == null) {
            throw new IllegalArgumentException("퇴원예고로 등록할 재원 환자를 선택해주세요.");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO csm.room_board_discharge_notice_%s
                (rbp_id, snapshot_id, ward_name, room_name, patient_no, patient_name,
                 discharge_date, discharge_time, status, note, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe);
        jdbcTemplate.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, patient.get("rbp_id"));
            ps.setObject(2, patient.get("rbs_id"));
            ps.setString(3, safeText(patient.get("ward_name"), 50));
            ps.setString(4, normalizeRoomName(Objects.toString(patient.get("room_name"), "")));
            ps.setString(5, safeText(patient.get("patient_no"), 100));
            ps.setString(6, safeText(patient.get("patient_name"), 100));
            ps.setObject(7, date);
            ps.setString(8, normalizedTime);
            ps.setString(9, normalizedStatus);
            ps.setString(10, safeText(note, 1000));
            ps.setString(11, safeText(actor, 100));
            ps.setString(12, safeText(actor, 100));
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    @Transactional
    public void updateDischargeNoticeStatus(String inst, long noticeId, String status, String actor) {
        ensureTables(inst);
        if (noticeId <= 0) {
            throw new IllegalArgumentException("noticeId is required");
        }
        String safe = sanitizeInst(inst);
        jdbcTemplate.update(
                "UPDATE csm.room_board_discharge_notice_" + safe + " SET status = ?, updated_by = ? WHERE rbdn_id = ?",
                normalizeDischargeStatus(status),
                safeText(actor, 100),
                noticeId);
    }

    @Transactional
    public int autoCompleteByTime(String inst, LocalDate dischargeDate, String dischargeTime) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        String normalizedTime = normalizeDischargeTime(dischargeTime);
        return jdbcTemplate.update(
                "UPDATE csm.room_board_discharge_notice_" + safe
                        + " SET status = 'COMPLETED', updated_by = 'system'"
                        + " WHERE discharge_date = ? AND discharge_time = ? AND status = 'PLANNED'",
                dischargeDate,
                normalizedTime);
    }

    public RoomBoardImportResult previewImport(String inst, String sourceType, String snapshotDate, String snapshotTime,
            String rawText) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardImportResult result = new RoomBoardImportResult();
        result.setSourceType(sourceType);
        result.setSnapshotDate(normalizeDateString(snapshotDate));
        result.setSnapshotTime(normalizeTimeString(snapshotTime));

        List<RoomBoardRoomConfig> activeConfigs = getActiveRoomConfigs(safe, parseDate(snapshotDate, LocalDate.now()));
        Map<String, String> wardByRoom = activeConfigs.stream()
                .collect(Collectors.toMap(
                        item -> normalizeRoomName(item.getRoomName()),
                        RoomBoardRoomConfig::getWardName,
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<String[]> rows = parseRawRows(rawText);
        if (rows.isEmpty()) {
            result.setMessage("붙여넣기 데이터가 없습니다.");
            return result;
        }

        String effectiveSourceType = detectSourceType(sourceType, rows);
        result.setSourceType(effectiveSourceType);

        List<RoomBoardImportRow> parsed;
        if ("WARD_DETAIL".equalsIgnoreCase(effectiveSourceType)) {
            parsed = parseWardDetailRows(rows);
        } else if ("CLICKSOFT".equalsIgnoreCase(effectiveSourceType)) {
            parsed = parseClicksoftRows(rows);
        } else if ("ROOM_SLOT".equalsIgnoreCase(effectiveSourceType)) {
            parsed = parseRoomSlotRows(rows);
        } else {
            parsed = parseExcelDetailRows(rows, wardByRoom);
        }

        parsed = parsed.stream()
                .filter(row -> !safeText(row.getRoomName(), 50).isBlank())
                .filter(row -> !safeText(row.getPatientName(), 100).isBlank())
                .collect(Collectors.toList());

        result.setParsedCount(parsed.size());
        result.setRows(parsed);
        result.setSkippedCount(Math.max(0, rows.size() - parsed.size()));
        long bedMissing = parsed.stream().filter(row -> row.getBedNo() == null).count();
        String message;
        if (parsed.isEmpty()) {
            message = "인식된 환자 데이터가 없습니다.";
        } else if (bedMissing > 0) {
            message = "미리보기 생성 완료 · 병상번호 미인식 " + bedMissing + "건은 빈 병상에 순차 배치됩니다.";
        } else {
            message = "미리보기 생성 완료";
        }
        result.setMessage(message);
        return result;
    }

    @Transactional
    public RoomBoardImportResult importSnapshot(String inst, String sourceType, String snapshotDate, String snapshotTime,
            String rawText, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardImportResult preview = previewImport(safe, sourceType, snapshotDate, snapshotTime, rawText);
        if (preview.getParsedCount() <= 0) {
            throw new IllegalArgumentException("저장할 환자 데이터가 없습니다.");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO csm.room_board_snapshot_%s
                    (source_type, snapshot_date, snapshot_time, raw_text, uploaded_by, parse_status, parse_message)
                    VALUES (?, ?, ?, ?, ?, 'SUCCESS', ?)
                    """.formatted(safe), new String[] { "rbs_id" });
            ps.setString(1, safeText(sourceType, 30));
            ps.setObject(2, parseDate(snapshotDate, LocalDate.now()));
            ps.setString(3, normalizeTimeString(snapshotTime));
            ps.setString(4, rawText == null ? "" : rawText);
            ps.setString(5, safeText(username, 100));
            ps.setString(6, preview.getMessage());
            return ps;
        }, keyHolder);

        long snapshotId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        preview.setSnapshotId(snapshotId);

        String insertSql = """
                INSERT INTO csm.room_board_patient_%s
                (rbs_id, ward_name, room_name, bed_no, patient_no, patient_name, gender, age, admission_date,
                 doctor_name, patient_type, disease_name, disease_code, phone_patient, phone_guardian, memo, raw_row)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe);
        for (RoomBoardImportRow row : preview.getRows()) {
            jdbcTemplate.update(insertSql,
                    snapshotId,
                    safeText(row.getWardName(), 50),
                    normalizeRoomName(row.getRoomName()),
                    row.getBedNo(),
                    safeText(row.getPatientNo(), 100),
                    safeText(row.getPatientName(), 100),
                    normalizeGender(row.getGender()),
                    safeText(row.getAge(), 20),
                    safeText(row.getAdmissionDate(), 20),
                    safeText(row.getDoctorName(), 100),
                    safeText(row.getPatientType(), 50),
                    safeText(row.getDiseaseName(), 200),
                    safeText(row.getDiseaseCode(), 100),
                    null,
                    safeText(row.getPhoneGuardian(), 50),
                    safeText(row.getMemo(), 1000),
                    buildRawRow(row));
        }
        return preview;
    }

    public RoomBoardView getBoard(String inst, String snapshotDate) {
        return getBoard(inst, snapshotDate, null);
    }

    public RoomBoardView getBoard(String inst, String snapshotDate, Long snapshotId) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardSnapshot snapshot = snapshotId != null
                ? findSnapshotById(safe, snapshotId)
                : findSnapshot(safe, snapshotDate);
        LocalDate baseDate = snapshot != null && snapshot.getSnapshotDate() != null
                ? parseDate(snapshot.getSnapshotDate(), LocalDate.now())
                : parseDate(snapshotDate, LocalDate.now());
        List<RoomBoardRoomConfig> activeConfigs = getActiveRoomConfigs(safe, baseDate);
        List<Map<String, Object>> patients = snapshot == null ? List.of() : loadSnapshotPatients(safe, snapshot.getId());
        Map<String, List<Map<String, Object>>> patientsByRoom = patients.stream()
                .collect(Collectors.groupingBy(row -> normalizeRoomName(String.valueOf(row.get("room_name")))));
        Map<String, List<Map<String, Object>>> completedAdmissionsByRoom = loadCompletedAdmissionsByRoom(
                safe,
                snapshot == null ? null : snapshot.getId());
        Map<String, List<String>> reservationNamesByRoom = loadReservationNamesByRoom(safe, baseDate);
        LocalDate dischargeNoticeDate = snapshotDate == null || snapshotDate.isBlank() ? LocalDate.now() : baseDate;
        Map<String, List<Map<String, Object>>> dischargeNoticesByRoom = loadDischargeNoticesByRoom(safe, dischargeNoticeDate);

        Map<String, RoomBoardWardView> wardMap = new LinkedHashMap<>();
        int totalLicensed = 0;
        int totalOccupied = 0;
        int totalAvailable = 0;

        for (RoomBoardRoomConfig config : activeConfigs) {
            String wardName = safeText(config.getWardName(), 50);
            String roomName = normalizeRoomName(config.getRoomName());
            List<Map<String, Object>> roomDischargeNotices = dischargeNoticesByRoom.getOrDefault(roomName, List.of());
            Map<String, Map<String, Object>> completedDischargeIndex = indexDischargeNotices(
                    roomDischargeNotices.stream()
                            .filter(n -> "COMPLETED".equals(n.get("status")))
                            .collect(Collectors.toList()));
            List<Map<String, Object>> roomPatients = mergePatients(
                    patientsByRoom.getOrDefault(roomName, List.of()),
                    completedAdmissionsByRoom.getOrDefault(roomName, List.of()))
                    .stream()
                    .filter(p -> !completedDischargeIndex.containsKey(dischargePatientKey(p)))
                    .collect(Collectors.toList());
            List<String> reservationNames = reservationNamesByRoom.getOrDefault(roomName, List.of());
            int occupiedCount = roomPatients.size();
            int licensedBeds = config.getLicensedBeds() == null ? 0 : config.getLicensedBeds();
            int availableBeds = effectiveAvailableBeds(config.getAvailableBeds(), licensedBeds);
            int availableCount = Math.max(availableBeds - occupiedCount - reservationNames.size(), 0);
            long afternoonAvailableCount = roomDischargeNotices.stream()
                    .filter(row -> "오후 입원 가능".equals(dischargeAvailabilityLabel(
                            Objects.toString(row.get("status"), ""),
                            Objects.toString(row.get("discharge_time"), ""))))
                    .count();

            RoomBoardRoomView room = new RoomBoardRoomView();
            room.setWardName(wardName);
            room.setRoomName(roomName);
            room.setLicensedBeds(licensedBeds);
            room.setAvailableBeds(availableBeds);
            room.setRoomGender(safeText(config.getRoomGender(), 10));
            room.setOccupiedCount(occupiedCount);
            room.setReservationCount(reservationNames.size());
            room.setAvailableCount(availableCount);
            room.setOccupancyRate(licensedBeds <= 0 ? 0d : round1(occupiedCount * 100d / licensedBeds));
            room.setCareType(safeText(config.getCareType(), 100));
            room.setStatusLabel(config.getStatusLabel());
            room.setStatusWalk("Y".equalsIgnoreCase(config.getStatusWalk()));
            room.setStatusDiaper("Y".equalsIgnoreCase(config.getStatusDiaper()));
            room.setStatusOxygen("Y".equalsIgnoreCase(config.getStatusOxygen()));
            room.setStatusSuction("Y".equalsIgnoreCase(config.getStatusSuction()));
            room.setNote(safeText(config.getNote(), 500));
            // 슬롯코드 뒷자리(병상번호)에 맞춰 환자를 고정 위치 배열로 배치한다.
            // 병상번호가 없거나 충돌/초과인 환자는 남는 빈 병상에 순차 배치된다.
            int slotSize = Math.max(licensedBeds, 8);
            List<Map<String, Object>> positionedPatients = positionPatientsByBed(roomPatients, slotSize);
            room.setGenderSummary(buildGenderSummary(roomPatients));
            room.setReservationNames(String.join(", ", reservationNames));
            room.setPatientSlots(buildPatientSlots(positionedPatients, reservationNames));
            room.setPatientCards(buildPatientCards(positionedPatients, reservationNames, baseDate));
            room.setDischargeNoticeCount(roomDischargeNotices.size());
            room.setAfternoonAvailableCount((int) afternoonAvailableCount);
            room.setDischargePatientNames(roomDischargeNotices.stream()
                    .map(row -> safeText(row.get("patient_name"), 100) + " " + dischargeTimeLabel(Objects.toString(row.get("discharge_time"), "")))
                    .collect(Collectors.joining(", ")));
            room.setDischargeSlotLabels(buildDischargeSlotLabels(positionedPatients, roomDischargeNotices));
            room.setDischargeSlotAvailability(buildDischargeSlotAvailability(positionedPatients, roomDischargeNotices));

            RoomBoardWardView ward = wardMap.computeIfAbsent(wardName, key -> {
                RoomBoardWardView item = new RoomBoardWardView();
                item.setWardName(key);
                item.setLicensedBeds(0);
                item.setOccupiedBeds(0);
                item.setAvailableBeds(0);
                item.setOccupancyRate(0d);
                return item;
            });
            ward.getRooms().add(room);
            ward.setLicensedBeds(ward.getLicensedBeds() + licensedBeds);
            ward.setOccupiedBeds(ward.getOccupiedBeds() + occupiedCount);
            ward.setAvailableBeds(ward.getAvailableBeds() + availableCount);

            totalLicensed += licensedBeds;
            totalOccupied += occupiedCount;
            totalAvailable += availableCount;
        }

        wardMap.values().forEach(ward -> ward.setOccupancyRate(
                ward.getLicensedBeds() == 0 ? 0d : round1(ward.getOccupiedBeds() * 100d / ward.getLicensedBeds())));

        RoomBoardView board = new RoomBoardView();
        board.setSnapshotId(snapshot == null ? null : snapshot.getId());
        board.setSnapshotDate(snapshot == null ? baseDate.format(DATE_FMT) : snapshot.getSnapshotDate());
        board.setSnapshotTime(snapshot == null ? "" : safeText(snapshot.getSnapshotTime(), 5));
        board.setTotalLicensedBeds(totalLicensed);
        board.setTotalOccupiedBeds(totalOccupied);
        board.setTotalAvailableBeds(totalAvailable);
        board.setTotalOccupancyRate(totalLicensed == 0 ? 0d : round1(totalOccupied * 100d / totalLicensed));
        board.setWards(wardMap.values().stream()
                .peek(ward -> ward.getRooms().sort(roomComparator()))
                .sorted(Comparator.comparing(RoomBoardWardView::getWardName, this::compareWardName))
                .collect(Collectors.toList()));
        return board;
    }

    private RoomBoardSnapshot findSnapshot(String inst, String snapshotDate) {
        String safe = sanitizeInst(inst);
        List<RoomBoardSnapshot> history;
        if (snapshotDate != null && !snapshotDate.isBlank()) {
            String sql = """
                    SELECT rbs_id, source_type, DATE_FORMAT(snapshot_date,'%%Y-%%m-%%d') AS snapshot_date,
                           snapshot_time, uploaded_by,
                           DATE_FORMAT(uploaded_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS uploaded_at,
                           parse_status, parse_message
                      FROM csm.room_board_snapshot_%s
                     WHERE snapshot_date = ?
                     ORDER BY rbs_id DESC
                     LIMIT 1
                    """.formatted(safe);
            history = jdbcTemplate.query(sql, (rs, rowNum) -> {
                RoomBoardSnapshot item = new RoomBoardSnapshot();
                item.setId(rs.getLong("rbs_id"));
                item.setSourceType(rs.getString("source_type"));
                item.setSnapshotDate(rs.getString("snapshot_date"));
                item.setSnapshotTime(rs.getString("snapshot_time"));
                item.setUploadedBy(rs.getString("uploaded_by"));
                item.setUploadedAt(rs.getString("uploaded_at"));
                item.setParseStatus(rs.getString("parse_status"));
                item.setParseMessage(rs.getString("parse_message"));
                return item;
            }, parseDate(snapshotDate, LocalDate.now()));
            if (!history.isEmpty()) {
                return history.get(0);
            }
        }
        history = getSnapshotHistory(safe, 1);
        return history.isEmpty() ? null : history.get(0);
    }

    private RoomBoardSnapshot findSnapshotById(String inst, Long snapshotId) {
        String safe = sanitizeInst(inst);
        if (snapshotId == null) {
            return findSnapshot(safe, null);
        }
        String sql = """
                SELECT rbs_id, source_type, DATE_FORMAT(snapshot_date,'%%Y-%%m-%%d') AS snapshot_date,
                       snapshot_time, uploaded_by,
                       DATE_FORMAT(uploaded_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS uploaded_at,
                       parse_status, parse_message
                  FROM csm.room_board_snapshot_%s
                 WHERE rbs_id = ?
                """.formatted(safe);
        List<RoomBoardSnapshot> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            RoomBoardSnapshot item = new RoomBoardSnapshot();
            item.setId(rs.getLong("rbs_id"));
            item.setSourceType(rs.getString("source_type"));
            item.setSnapshotDate(rs.getString("snapshot_date"));
            item.setSnapshotTime(rs.getString("snapshot_time"));
            item.setUploadedBy(rs.getString("uploaded_by"));
            item.setUploadedAt(rs.getString("uploaded_at"));
            item.setParseStatus(rs.getString("parse_status"));
            item.setParseMessage(rs.getString("parse_message"));
            return item;
        }, snapshotId);
        return rows.isEmpty() ? findSnapshot(safe, null) : rows.get(0);
    }

    private List<Map<String, Object>> loadSnapshotPatients(String inst, Long snapshotId) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbp_id, patient_no, room_name, bed_no, ward_name, patient_name, gender, age,
                       admission_date, patient_type, disease_code, disease_name, phone_guardian
                  FROM csm.room_board_patient_%s
                 WHERE rbs_id = ?
                 ORDER BY room_name ASC, (bed_no IS NULL), bed_no ASC, patient_name ASC
                """.formatted(safe);
        return jdbcTemplate.queryForList(sql, snapshotId);
    }

    private Map<String, List<Map<String, Object>>> loadCompletedAdmissionsByRoom(String inst, Long snapshotId) {
        String safe = sanitizeInst(inst);
        String snapshotFilter = snapshotId == null ? "" : """
                   AND DATE(cd.updated_at) >= (
                       SELECT snapshot_date
                         FROM csm.room_board_snapshot_%s
                        WHERE rbs_id = ?
                   )
                """.formatted(safe);
        String sql = """
                SELECT cd.cs_idx, cd.cs_col_01 AS patient_name_hex, cd.cs_col_02 AS gender,
                       cd.cs_col_38 AS room_name
                  FROM csm.counsel_data_%s cd
                 WHERE cd.cs_col_19 = '입원완료'
                   AND cd.cs_col_38 IS NOT NULL
                   AND TRIM(cd.cs_col_38) <> ''
                %s
                 ORDER BY cd.updated_at ASC, cd.cs_idx ASC
                """.formatted(safe, snapshotFilter);
        List<Map<String, Object>> rows = snapshotId == null
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, snapshotId);
        List<Map<String, Object>> patients = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String roomName = normalizeRoomName(Objects.toString(row.get("room_name"), ""));
            if (roomName.isBlank()) {
                continue;
            }
            String patientName = decryptHexString(safeText(row.get("patient_name_hex"), 500));
            if (patientName.isBlank()) {
                patientName = "입원완료환자";
            }
            Map<String, Object> patient = new LinkedHashMap<>();
            patient.put("room_name", roomName);
            patient.put("patient_name", patientName);
            patient.put("gender", normalizeGender(safeText(row.get("gender"), 20)));
            patients.add(patient);
        }
        return patients.stream()
                .collect(Collectors.groupingBy(row -> normalizeRoomName(Objects.toString(row.get("room_name"), ""))));
    }

    private List<Map<String, Object>> mergePatients(
            List<Map<String, Object>> snapshotPatients,
            List<Map<String, Object>> completedAdmissions) {
        List<Map<String, Object>> merged = new ArrayList<>(snapshotPatients);
        for (Map<String, Object> admission : completedAdmissions) {
            String admissionName = safeText(admission.get("patient_name"), 100);
            String admissionRoom = normalizeRoomName(Objects.toString(admission.get("room_name"), ""));
            boolean exists = merged.stream().anyMatch(patient ->
                    admissionName.equals(safeText(patient.get("patient_name"), 100))
                            && admissionRoom.equals(normalizeRoomName(Objects.toString(patient.get("room_name"), ""))));
            if (!exists) {
                merged.add(admission);
            }
        }
        return merged;
    }

    private Map<String, List<Map<String, Object>>> loadDischargeNoticesByRoom(String inst, LocalDate date) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbdn_id, rbp_id, snapshot_id, ward_name, room_name, patient_no, patient_name,
                       discharge_time, status, note
                  FROM csm.room_board_discharge_notice_%s
                 WHERE discharge_date = ?
                   AND status <> 'CANCELLED'
                 ORDER BY room_name ASC, patient_name ASC
                """.formatted(safe);
        return jdbcTemplate.queryForList(sql, date).stream()
                .collect(Collectors.groupingBy(row -> normalizeRoomName(Objects.toString(row.get("room_name"), ""))));
    }

    private List<RoomBoardRoomConfig> getActiveRoomConfigs(String inst, LocalDate baseDate) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbm_id, ward_name, room_name, DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date, licensed_beds, available_beds, room_gender, care_type,
                       status_walk, status_diaper, status_oxygen, status_suction,
                       nursing_cost, note, use_yn
                  FROM csm.room_board_room_master_%s
                 WHERE use_yn='Y'
                   AND start_date <= ?
                   AND end_date >= ?
                """.formatted(safe);
        List<RoomBoardRoomConfig> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            RoomBoardRoomConfig item = new RoomBoardRoomConfig();
            item.setId(rs.getLong("rbm_id"));
            item.setWardName(rs.getString("ward_name"));
            item.setRoomName(rs.getString("room_name"));
            item.setStartDate(rs.getString("start_date"));
            item.setEndDate(rs.getString("end_date"));
            item.setLicensedBeds(rs.getInt("licensed_beds"));
            int availableBeds = rs.getInt("available_beds");
            item.setAvailableBeds(rs.wasNull() ? null : availableBeds);
            item.setRoomGender(rs.getString("room_gender"));
            item.setCareType(rs.getString("care_type"));
            item.setStatusWalk(rs.getString("status_walk"));
            item.setStatusDiaper(rs.getString("status_diaper"));
            item.setStatusOxygen(rs.getString("status_oxygen"));
            item.setStatusSuction(rs.getString("status_suction"));
            item.setNursingCost(rs.getString("nursing_cost"));
            item.setNote(rs.getString("note"));
            item.setUseYn(rs.getString("use_yn"));
            return item;
        }, baseDate, baseDate);
        items.sort(Comparator.comparing(RoomBoardRoomConfig::getWardName, this::compareWardName)
                .thenComparing(RoomBoardRoomConfig::getRoomName, this::compareRoomName)
                .thenComparing(RoomBoardRoomConfig::getStartDate, Comparator.nullsLast(String::compareTo)));
        return items;
    }

    private Map<String, List<String>> loadReservationNamesByRoom(String inst, LocalDate baseDate) {
        String safe = sanitizeInst(inst);
        Map<String, List<String>> out = new HashMap<>();
        String sql = """
                SELECT cs_idx, cs_col_01 AS patient_name_hex, cs_col_38, cs_col_21
                  FROM csm.counsel_data_%s
                 WHERE cs_col_19 = '입원예약'
                   AND cs_col_38 IS NOT NULL
                   AND TRIM(cs_col_38) <> ''
                """.formatted(safe);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            String roomName = normalizeRoomName(String.valueOf(row.get("cs_col_38")));
            if (roomName.isBlank()) {
                continue;
            }
            String reservedAt = safeText(row.get("cs_col_21"), 100);
            if (!reservedAt.isBlank()) {
                String datePrefix = reservedAt.length() >= 10 ? reservedAt.substring(0, 10) : reservedAt;
                LocalDate reservedDate = parseDate(datePrefix, null);
                if (reservedDate != null && reservedDate.isBefore(baseDate)) {
                    continue;
                }
            }
            String patientName = decryptHexString(safeText(row.get("patient_name_hex"), 500));
            if (patientName.isBlank()) {
                patientName = "예약환자";
            }
            out.computeIfAbsent(roomName, key -> new ArrayList<>()).add(patientName);
        }
        return out;
    }

    /**
     * 환자를 병상번호(bed_no) 위치에 고정 배치한 배열을 만든다. 병상번호가 없거나
     * 범위를 벗어나거나 중복인 환자는 남는 빈 병상에 순서대로 채운다. 빈 병상은 null.
     */
    private List<Map<String, Object>> positionPatientsByBed(List<Map<String, Object>> roomPatients, int size) {
        List<Map<String, Object>> slots = new ArrayList<>(Collections.nCopies(size, null));
        List<Map<String, Object>> overflow = new ArrayList<>();
        for (Map<String, Object> patient : roomPatients) {
            if (patient == null || safeText(patient.get("patient_name"), 100).isBlank()) {
                continue;
            }
            Integer bed = toInt(patient.get("bed_no"));
            if (bed != null && bed >= 1 && bed <= size && slots.get(bed - 1) == null) {
                slots.set(bed - 1, patient);
            } else {
                overflow.add(patient);
            }
        }
        int cursor = 0;
        for (Map<String, Object> patient : overflow) {
            while (cursor < size && slots.get(cursor) != null) {
                cursor++;
            }
            if (cursor >= size) {
                break;
            }
            slots.set(cursor, patient);
        }
        return slots;
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> buildPatientSlots(List<Map<String, Object>> positionedPatients, List<String> reservationNames) {
        Deque<String> reservations = reservationQueue(reservationNames);
        List<String> slots = new ArrayList<>(positionedPatients.size());
        for (Map<String, Object> patient : positionedPatients) {
            if (patient != null) {
                slots.add(safeText(patient.get("patient_name"), 100));
            } else if (!reservations.isEmpty()) {
                slots.add(reservations.poll() + " (예약)");
            } else {
                slots.add("-");
            }
        }
        return slots;
    }

    /**
     * Per-bed patient cards for the board view. The index aligns with
     * {@link #positionPatientsByBed} so 병상번호 위치가 그대로 유지되고, 빈 병상은
     * 예약(있으면) → 빈칸 순으로 채워 discharge slot 배열과 인덱스가 일치한다.
     */
    private List<Map<String, Object>> buildPatientCards(
            List<Map<String, Object>> positionedPatients, List<String> reservationNames, LocalDate baseDate) {
        Deque<String> reservations = reservationQueue(reservationNames);
        List<Map<String, Object>> cards = new ArrayList<>(positionedPatients.size());
        for (Map<String, Object> patient : positionedPatients) {
            if (patient != null) {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("kind", "patient");
                card.put("name", safeText(patient.get("patient_name"), 100));
                card.put("chartNo", safeText(patient.get("patient_no"), 100));
                card.put("type", safeText(patient.get("patient_type"), 50));
                card.put("age", safeText(patient.get("age"), 20));
                card.put("gender", genderLabel(safeText(patient.get("gender"), 10)));
                String admissionDate = safeText(patient.get("admission_date"), 20);
                card.put("admissionDate", admissionDate);
                card.put("days", admissionDays(admissionDate, baseDate));
                card.put("diseaseCode", safeText(patient.get("disease_code"), 100));
                card.put("diseaseName", safeText(patient.get("disease_name"), 200));
                card.put("guardianPhone", safeText(patient.get("phone_guardian"), 50));
                cards.add(card);
            } else if (!reservations.isEmpty()) {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("kind", "reservation");
                card.put("name", reservations.poll());
                cards.add(card);
            } else {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("kind", "empty");
                cards.add(empty);
            }
        }
        return cards;
    }

    private Deque<String> reservationQueue(List<String> reservationNames) {
        Deque<String> queue = new ArrayDeque<>();
        if (reservationNames != null) {
            for (String reservationName : reservationNames) {
                String name = safeText(reservationName, 100);
                if (!name.isBlank()) {
                    queue.add(name);
                }
            }
        }
        return queue;
    }

    /** 입원일수: 입원일부터 기준일까지의 경과 일수(입원일=1일차). 유효하지 않으면 null. */
    Integer admissionDays(String admissionDate, LocalDate baseDate) {
        LocalDate admitted = parseDate(admissionDate, null);
        if (admitted == null || baseDate == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(admitted, baseDate) + 1;
        return days < 1 ? null : (int) days;
    }

    String genderLabel(String gender) {
        String value = safeText(gender, 10).toUpperCase(Locale.ROOT);
        if (value.equals("M") || value.equals("남")) {
            return "남";
        }
        if (value.equals("F") || value.equals("여")) {
            return "여";
        }
        return safeText(gender, 10);
    }

    private List<String> buildDischargeSlotLabels(
            List<Map<String, Object>> positionedPatients,
            List<Map<String, Object>> dischargeNotices) {
        Map<String, Map<String, Object>> byPatientKey = indexDischargeNotices(dischargeNotices);
        List<String> labels = new ArrayList<>(positionedPatients.size());
        for (Map<String, Object> patient : positionedPatients) {
            Map<String, Object> notice = patient == null ? null : byPatientKey.get(dischargePatientKey(patient));
            labels.add(notice == null ? "" : dischargeTimeLabel(Objects.toString(notice.get("discharge_time"), "")) + "퇴원");
        }
        return labels;
    }

    private List<String> buildDischargeSlotAvailability(
            List<Map<String, Object>> positionedPatients,
            List<Map<String, Object>> dischargeNotices) {
        Map<String, Map<String, Object>> byPatientKey = indexDischargeNotices(dischargeNotices);
        List<String> labels = new ArrayList<>(positionedPatients.size());
        for (Map<String, Object> patient : positionedPatients) {
            Map<String, Object> notice = patient == null ? null : byPatientKey.get(dischargePatientKey(patient));
            labels.add(notice == null ? "" : dischargeAvailabilityLabel(
                    Objects.toString(notice.get("status"), ""),
                    Objects.toString(notice.get("discharge_time"), "")));
        }
        return labels;
    }

    private Map<String, Map<String, Object>> indexDischargeNotices(List<Map<String, Object>> dischargeNotices) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (dischargeNotices == null) {
            return out;
        }
        for (Map<String, Object> notice : dischargeNotices) {
            String key = dischargePatientKey(notice);
            if (!key.isBlank()) {
                out.putIfAbsent(key, notice);
            }
            // Also register a name-based fallback so that patients coming through
            // loadCompletedAdmissionsByRoom (no rbp_id in their map) and patients
            // whose rbp_id changed after a snapshot re-import still match.
            String nameKey = "name:" + normalizeRoomName(Objects.toString(notice.get("room_name"), ""))
                    + ":" + safeText(notice.get("patient_name"), 100);
            out.putIfAbsent(nameKey, notice);
        }
        return out;
    }

    private String dischargePatientKey(Map<String, Object> row) {
        String rbpId = Objects.toString(row.get("rbp_id"), "").trim();
        if (!rbpId.isBlank()) {
            return "id:" + rbpId;
        }
        String patientNo = safeText(row.get("patient_no"), 100);
        if (!patientNo.isBlank()) {
            return "no:" + patientNo;
        }
        return "name:" + normalizeRoomName(Objects.toString(row.get("room_name"), ""))
                + ":" + safeText(row.get("patient_name"), 100);
    }

    private String buildGenderSummary(List<Map<String, Object>> roomPatients) {
        boolean hasM = false;
        boolean hasF = false;
        for (Map<String, Object> row : roomPatients) {
            String gender = normalizeGender(safeText(row.get("gender"), 10));
            if ("M".equals(gender)) {
                hasM = true;
            } else if ("F".equals(gender)) {
                hasF = true;
            }
        }
        if (hasM && hasF) {
            return "FM";
        }
        if (hasF) {
            return "F";
        }
        if (hasM) {
            return "M";
        }
        return "";
    }

    private Comparator<RoomBoardRoomView> roomComparator() {
        return (a, b) -> compareRoomName(a.getRoomName(), b.getRoomName());
    }

    private int compareWardName(String a, String b) {
        return compareMixed(a, b);
    }

    private int compareRoomName(String a, String b) {
        return compareMixed(a, b);
    }

    private int compareMixed(String a, String b) {
        String left = safeText(a, 100);
        String right = safeText(b, 100);
        int leftNum = extractFirstNumber(left);
        int rightNum = extractFirstNumber(right);
        if (leftNum != rightNum) {
            return Integer.compare(leftNum, rightNum);
        }
        return left.compareTo(right);
    }

    private int extractFirstNumber(String text) {
        if (text == null) {
            return Integer.MAX_VALUE;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private List<String[]> parseRawRows(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawText.replace("\r", "").split("\n"))
                .map(line -> line.replace('\u00A0', ' '))
                .map(line -> line.replace("\u2007", " "))
                .map(line -> line.replace("\u202F", " "))
                .map(String::stripTrailing)
                .filter(line -> !line.strip().isBlank())
                .map(this::splitRawLine)
                .collect(Collectors.toList());
    }

    private String[] splitRawLine(String line) {
        if (line == null) {
            return new String[0];
        }
        if (line.contains("\t")) {
            return line.strip().split("\t", -1);
        }
        return line.trim().split("\\s{2,}", -1);
    }

    private List<RoomBoardRoomConfig> parseRoomConfigRows(List<String[]> rows) {
        List<RoomBoardRoomConfig> out = new ArrayList<>();
        for (String[] row : rows) {
            if (isRoomConfigHeaderRow(row)) {
                continue;
            }
            RoomBoardRoomConfig item = parseRoomConfigRow(row);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private RoomBoardRoomConfig parseRoomConfigRow(String[] row) {
        if (row == null || row.length == 0) {
            return null;
        }
        String wardName = safeText(getCell(row, 0), 50);
        String roomName = normalizeRoomName(getCell(row, 1));
        LocalDate startDate = parseDate(getCell(row, 2), null);
        LocalDate endDate = parseDate(getCell(row, 3), null);
        int licensedBeds = parseInt(getCell(row, 4), 0);

        if (wardName.isBlank() && roomName.isBlank() && startDate == null && endDate == null && licensedBeds == 0) {
            return null;
        }
        if (wardName.isBlank() || roomName.isBlank() || startDate == null || endDate == null || licensedBeds <= 0) {
            return null;
        }

        List<String> tailValues = extractRoomConfigTailValues(row);
        RoomBoardRoomConfig item = new RoomBoardRoomConfig();
        item.setWardName(wardName);
        item.setRoomName(roomName);
        item.setStartDate(startDate.format(DATE_FMT));
        item.setEndDate(endDate.format(DATE_FMT));
        item.setLicensedBeds(licensedBeds);
        item.setCareType(safeText(getCell(row, 5), 100));
        item.setStatusWalk(normalizeBooleanYn(getCell(row, 7)));
        item.setStatusDiaper(normalizeBooleanYn(getCell(row, 8)));
        item.setStatusOxygen(normalizeBooleanYn(getCell(row, 9)));
        item.setStatusSuction(normalizeBooleanYn(getCell(row, 10)));
        item.setNursingCost(tailValues.size() > 0 ? safeText(tailValues.get(0), 100) : "");
        item.setNote(tailValues.size() > 1 ? safeText(tailValues.get(1), 500) : "");
        item.setUseYn("Y");
        return item;
    }

    private boolean isRoomConfigHeaderRow(String[] row) {
        return hasExactToken(row, "병동", "병실구성", "개시일자", "허가병상수")
                || hasExactToken(row, "상태구분", "간병 비용", "참고내용");
    }

    private List<String> extractRoomConfigTailValues(String[] row) {
        List<String> out = new ArrayList<>();
        for (int i = 11; i < row.length; i++) {
            String value = safeText(row[i], 500);
            if (value.isBlank() || isBooleanCell(value)) {
                continue;
            }
            out.add(value);
        }
        return out;
    }

    private List<RoomBoardImportRow> parseExcelDetailRows(List<String[]> rows, Map<String, String> wardByRoom) {
        List<RoomBoardImportRow> out = new ArrayList<>();
        int startIdx = hasAnyToken(rows.get(0), "병실", "환자번호", "환자이름") ? 1 : 0;
        for (int i = startIdx; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String roomName = normalizeRoomName(getCell(row, 0));
            String patientName = safeText(getCell(row, 2), 100);
            if (roomName.isBlank() || patientName.isBlank()) {
                continue;
            }
            RoomBoardImportRow item = new RoomBoardImportRow();
            item.setRoomName(roomName);
            item.setBedNo(extractBedNo(getCell(row, 0)));
            item.setWardName(safeText(wardByRoom.get(roomName), 50));
            item.setPatientNo(getCell(row, 1));
            item.setPatientName(patientName);
            item.setAdmissionDate(getCell(row, 3));
            item.setDoctorName(getCell(row, 10));
            item.setAge(getCell(row, 15));
            item.setGender(getCell(row, 16));
            item.setPatientType(getCell(row, 11));
            item.setMemo(getCell(row, 24));
            out.add(item);
        }
        return out;
    }

    private List<RoomBoardImportRow> parseClicksoftRows(List<String[]> rows) {
        List<RoomBoardImportRow> out = new ArrayList<>();
        int startIdx = hasAnyToken(rows.get(0), "병동", "병실", "차트번호") ? 1 : 0;
        for (int i = startIdx; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String wardName = safeText(getCell(row, 0), 50);
            String rawRoomCell = firstNonBlank(getCell(row, 1), getCell(row, 3));
            String roomName = normalizeRoomName(rawRoomCell);
            String patientName = safeText(getCell(row, 6), 100);
            if (roomName.isBlank() || patientName.isBlank()) {
                continue;
            }
            RoomBoardImportRow item = new RoomBoardImportRow();
            item.setWardName(wardName);
            item.setRoomName(roomName);
            item.setBedNo(extractBedNo(rawRoomCell));
            item.setPatientNo(getCell(row, 7));
            item.setPatientName(patientName);
            item.setDoctorName(getCell(row, 5));
            item.setPatientType(getCell(row, 8));
            item.setAge(getCell(row, 9));
            item.setGender(getCell(row, 10));
            item.setAdmissionDate(getCell(row, 11));
            item.setMemo("");
            out.add(item);
        }
        return out;
    }

    private List<RoomBoardImportRow> parseRoomSlotRows(List<String[]> rows) {
        List<RoomBoardImportRow> out = new ArrayList<>();
        for (String[] row : rows) {
            String slotCode = safeText(getCell(row, 0), 100);
            String patientName = safeText(getCell(row, 3), 100);
            if (slotCode.isBlank() || patientName.isBlank()) {
                continue;
            }

            ParsedSlot parsedSlot = parseSlotCode(slotCode);
            if (parsedSlot == null) {
                continue;
            }

            RoomBoardImportRow item = new RoomBoardImportRow();
            item.setWardName(parsedSlot.wardName());
            item.setRoomName(parsedSlot.roomName());
            item.setBedNo(parsedSlot.bedNo());
            item.setDoctorName(getCell(row, 2));
            item.setPatientName(patientName);
            item.setPatientNo(getCell(row, 4));
            item.setPatientType(getCell(row, 5));
            item.setAge(getCell(row, 6));
            item.setGender(getCell(row, 7));
            item.setAdmissionDate(getCell(row, 8));
            item.setMemo("");
            out.add(item);
        }
        return out;
    }

    /**
     * Rich ward-detail export (병실현황판 상세조회). The slot code (e.g. "3병동-0319-04")
     * sits at column 0 or 1 (a leading blank grouping column is common); all other
     * fields are positioned relative to that slot column. This is the only source
     * that carries 상병코드/상병명/보호자전화/유형(보험).
     */
    private List<RoomBoardImportRow> parseWardDetailRows(List<String[]> rows) {
        List<RoomBoardImportRow> out = new ArrayList<>();
        boolean headerSkipped = false;
        for (String[] row : rows) {
            if (!headerSkipped && hasAnyToken(row, "상병코드", "수진자", "차트번호", "보호자전화번호")) {
                headerSkipped = true;
                continue;
            }
            int base = wardDetailSlotIndex(row);
            if (base < 0) {
                continue;
            }
            ParsedSlot slot = parseSlotCode(getCell(row, base));
            if (slot == null) {
                continue;
            }
            String patientName = safeText(getCell(row, base + 4), 100);
            if (patientName.isBlank()) {
                continue;
            }
            RoomBoardImportRow item = new RoomBoardImportRow();
            item.setWardName(slot.wardName());
            item.setRoomName(slot.roomName());
            item.setBedNo(slot.bedNo());
            item.setDoctorName(getCell(row, base + 3));
            item.setPatientName(patientName);
            item.setPatientNo(getCell(row, base + 5));
            item.setPatientType(getCell(row, base + 6));
            item.setAge(getCell(row, base + 7));
            item.setGender(getCell(row, base + 8));
            item.setAdmissionDate(getCell(row, base + 9));
            // 보호자전화번호 전용 칸만 사용한다(휴대전화/전화번호는 피보자 번호라 보호자와 다름).
            item.setPhoneGuardian(getCell(row, base + 19));
            item.setDiseaseCode(getCell(row, base + 24));
            item.setDiseaseName(getCell(row, base + 25));
            item.setMemo(firstNonBlank(getCell(row, base + 13), getCell(row, base + 23)));
            out.add(item);
        }
        return out;
    }

    private int wardDetailSlotIndex(String[] row) {
        for (int i = 0; i <= 1 && i < row.length; i++) {
            if (looksLikeRoomSlotCode(getCell(row, i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean looksLikeWardDetail(String[] headerRow) {
        return hasAnyToken(headerRow, "상병코드", "상병명칭", "보호자전화번호");
    }

    private String detectSourceType(String sourceType, List<String[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return sourceType;
        }
        String requested = safeText(sourceType, 30);
        if ("CLICKSOFT".equalsIgnoreCase(requested) || "ROOM_SLOT".equalsIgnoreCase(requested)
                || "WARD_DETAIL".equalsIgnoreCase(requested)) {
            return requested;
        }
        if (looksLikeWardDetail(rows.get(0))) {
            return "WARD_DETAIL";
        }
        String firstCell = safeText(getCell(rows.get(0), 0), 100);
        if (looksLikeRoomSlotCode(firstCell)) {
            return "ROOM_SLOT";
        }
        return requested.isBlank() ? "EXCEL_DETAIL" : requested;
    }

    private boolean hasAnyToken(String[] row, String... tokens) {
        if (row == null || row.length == 0) {
            return false;
        }
        String joined = Arrays.stream(row).filter(Objects::nonNull).collect(Collectors.joining("|"));
        for (String token : tokens) {
            if (joined.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExactToken(String[] row, String... tokens) {
        if (row == null || row.length == 0 || tokens == null || tokens.length == 0) {
            return false;
        }
        for (String cell : row) {
            String normalized = safeText(cell, 100).replace(" ", "");
            for (String token : tokens) {
                String normalizedToken = safeText(token, 100).replace(" ", "");
                if (!normalized.isBlank() && normalized.equals(normalizedToken)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getCell(String[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length) {
            return "";
        }
        return safeText(row[idx], 1000);
    }

    private String buildRawRow(RoomBoardImportRow row) {
        return String.join("\t",
                safeText(row.getWardName(), 50),
                safeText(row.getRoomName(), 50),
                safeText(row.getPatientNo(), 100),
                safeText(row.getPatientName(), 100),
                safeText(row.getGender(), 10),
                safeText(row.getAge(), 20),
                safeText(row.getAdmissionDate(), 20),
                safeText(row.getDoctorName(), 100),
                safeText(row.getPatientType(), 50),
                safeText(row.getDiseaseCode(), 100),
                safeText(row.getDiseaseName(), 200),
                safeText(row.getPhoneGuardian(), 50),
                safeText(row.getMemo(), 1000));
    }

    private String normalizeGender(String gender) {
        String value = safeText(gender, 20).toUpperCase(Locale.ROOT);
        if (value.equals("남") || value.equals("남성") || value.equals("M")) {
            return "M";
        }
        if (value.equals("여") || value.equals("여성") || value.equals("F")) {
            return "F";
        }
        return value;
    }

    private String normalizeRoomName(String roomName) {
        String value = safeText(roomName, 50).replace(" ", "");
        if (value.endsWith("실")) {
            value = value.substring(0, value.length() - 1) + "호";
        }
        return value;
    }

    private ParsedSlot parseSlotCode(String slotCode) {
        String[] parts = safeText(slotCode, 100).split("-");
        if (parts.length < 2) {
            return null;
        }
        String wardName = safeText(parts[0], 50);
        String rawRoom = safeText(parts[1], 20).replaceAll("[^0-9]", "");
        if (wardName.isBlank() || rawRoom.isBlank()) {
            return null;
        }
        int roomNumber = parseInt(rawRoom, -1);
        if (roomNumber <= 0) {
            return null;
        }
        // 슬롯코드 뒷자리(예: "3병동-0319-04"의 "04")가 병상번호.
        Integer bedNo = null;
        if (parts.length >= 3) {
            String rawBed = safeText(parts[2], 20).replaceAll("[^0-9]", "");
            int parsedBed = parseInt(rawBed, -1);
            if (parsedBed > 0) {
                bedNo = parsedBed;
            }
        }
        return new ParsedSlot(wardName, roomNumber + "호", bedNo);
    }

    /**
     * 슬롯코드가 아닌 원본(엑셀·클릭소프트)의 병실 셀에서 병상번호를 추출한다.
     * 뒷자리 숫자가 병상번호 규칙을 따른다: "319-04" / "0319-4" 형태는 대시 뒤를,
     * "31904"처럼 붙어 있으면 마지막 2자리를 병상번호로 본다. 식별 불가 시 null.
     */
    private Integer extractBedNo(String roomCell) {
        String value = safeText(roomCell, 50);
        if (value.isBlank()) {
            return null;
        }
        int dash = value.lastIndexOf('-');
        if (dash >= 0 && dash < value.length() - 1) {
            int bed = parseInt(value.substring(dash + 1).replaceAll("[^0-9]", ""), -1);
            return bed > 0 ? bed : null;
        }
        // 구분자가 없을 때는 RRRR+BB(방4자리+병상2자리=최소 5자리) 조합만 병상으로 본다.
        // "319"/"0319"처럼 짧은 순수 호실번호는 오인식하지 않도록 제외.
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() >= 5) {
            int bed = parseInt(digits.substring(digits.length() - 2), -1);
            return bed > 0 ? bed : null;
        }
        return null;
    }

    private boolean looksLikeRoomSlotCode(String value) {
        String normalized = safeText(value, 100);
        return normalized.matches(".+?-\\d{4}-\\d{2}");
    }

    private String normalizeBooleanYn(String value) {
        return isTruthy(value) ? "Y" : "N";
    }

    private String normalizeYn(String value) {
        return "Y".equalsIgnoreCase(safeText(value, 1)) ? "Y" : "N";
    }

    private String normalizeDateString(String date) {
        return parseDate(date, LocalDate.now()).format(DATE_FMT);
    }

    private String normalizeTimeString(String time) {
        String value = safeText(time, 5);
        if (value.matches("\\d{2}:\\d{2}")) {
            return value;
        }
        return "";
    }

    private String safeText(Object value, int maxLen) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String text = safeText(value, 100);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private boolean isBooleanCell(String value) {
        String normalized = safeText(value, 20).toUpperCase(Locale.ROOT);
        return normalized.equals("TRUE") || normalized.equals("FALSE")
                || normalized.equals("Y") || normalized.equals("N");
    }

    private boolean isTruthy(String value) {
        String normalized = safeText(value, 20).toUpperCase(Locale.ROOT);
        return normalized.equals("TRUE") || normalized.equals("Y")
                || normalized.equals("1") || normalized.equals("사용");
    }

    private int parseInt(String value, int defaultValue) {
        String normalized = safeText(value, 20).replaceAll("[^0-9-]", "");
        if (normalized.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private LocalDate parseDate(String value, LocalDate defaultValue) {
        String text = safeText(value, 20);
        if (text.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(text.substring(0, Math.min(text.length(), 10)), DATE_FMT);
        } catch (DateTimeParseException e) {
            return defaultValue;
        }
    }

    private double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private String decryptHexString(String hex) {
        if (hex == null || hex.isBlank()) {
            return "";
        }
        try {
            byte[] encrypted = hexToBytes(hex.trim());
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKey = new SecretKeySpec(Arrays.copyOf(aesKey.getBytes(StandardCharsets.UTF_8), 16),
                    "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).trim();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.debug("[room-board] patient name decrypt fail: {}", e.toString());
            return "";
        }
    }

    private byte[] hexToBytes(String hex) {
        if ((hex.length() % 2) != 0) {
            throw new IllegalArgumentException("invalid hex");
        }
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            data[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return data;
    }

    private void upsertRoomConfigByPeriod(String inst, RoomBoardRoomConfig config, String username) {
        String safe = sanitizeInst(inst);
        PreparedRoomConfig prepared = prepareRoomConfig(config);
        String selectSql = """
                SELECT rbm_id
                  FROM csm.room_board_room_master_%s
                 WHERE ward_name=?
                   AND room_name=?
                   AND start_date=?
                   AND end_date=?
                 LIMIT 1
                """.formatted(safe);
        List<Long> ids = jdbcTemplate.query(selectSql,
                (rs, rowNum) -> rs.getLong("rbm_id"),
                prepared.wardName(),
                prepared.roomName(),
                prepared.startDate(),
                prepared.endDate());
        if (ids.isEmpty()) {
            insertRoomConfig(safe, prepared, username);
            return;
        }
        updateRoomConfig(safe, ids.get(0), prepared, username);
    }

    /** 기존 병실 기준정보의 (병동·병실·기간)을 조회한다. 겹침 검사용. */
    private List<RoomConfigPeriod> loadRoomConfigPeriods(String safe) {
        String sql = """
                SELECT ward_name, room_name,
                       DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date
                  FROM csm.room_board_room_master_%s
                """.formatted(safe);
        return jdbcTemplate.query(sql, (rs, n) -> new RoomConfigPeriod(
                rs.getString("ward_name"),
                normalizeRoomName(rs.getString("room_name")),
                parseDate(rs.getString("start_date"), null),
                parseDate(rs.getString("end_date"), null)));
    }

    /**
     * 붙여넣기로 들어온 기준정보가 (a) 기존 활성/이력 기간과 겹치거나 (b) 붙여넣기 내부에서
     * 같은 병실끼리 겹치는지 검사한다. 동일 기간(병동·병실·개시·종료 일치)은 갱신이므로 제외한다.
     * 겹침을 허용하면 한 병실이 활성 행 2개로 잡혀 현황판에서 해당 병동 병실 목록이 통째로 깨진다.
     */
    List<String> detectRoomConfigConflicts(List<RoomConfigPeriod> existing, List<RoomBoardRoomConfig> parsed) {
        List<String> conflicts = new ArrayList<>();
        List<RoomConfigPeriod> batch = new ArrayList<>();
        for (RoomBoardRoomConfig row : parsed) {
            String ward = safeText(row.getWardName(), 50);
            String room = normalizeRoomName(row.getRoomName());
            LocalDate ns = parseDate(row.getStartDate(), null);
            LocalDate ne = parseDate(row.getEndDate(), null);
            if (ward.isBlank() || room.isBlank() || ns == null || ne == null) {
                continue;
            }
            for (RoomConfigPeriod e : existing) {
                if (e.start() == null || e.end() == null || !sameRoom(e, ward, room)) {
                    continue;
                }
                if (e.start().isEqual(ns) && e.end().isEqual(ne)) {
                    continue; // 동일 기간 = 갱신
                }
                if (periodsOverlap(ns, ne, e.start(), e.end())) {
                    conflicts.add(room + ": 기존 기준정보(" + e.start() + "~" + e.end() + ")와 기간이 겹칩니다");
                }
            }
            for (RoomConfigPeriod b : batch) {
                if (sameRoom(b, ward, room) && periodsOverlap(ns, ne, b.start(), b.end())) {
                    conflicts.add(room + ": 붙여넣기 내 동일 병실 기간이 겹칩니다(" + b.start() + "~" + b.end() + ")");
                }
            }
            batch.add(new RoomConfigPeriod(ward, room, ns, ne));
        }
        return conflicts.stream().distinct().collect(Collectors.toList());
    }

    private boolean sameRoom(RoomConfigPeriod p, String ward, String room) {
        return p.wardName() != null && p.roomName() != null
                && p.wardName().equalsIgnoreCase(ward) && p.roomName().equalsIgnoreCase(room);
    }

    /** 두 폐구간 [s1,e1], [s2,e2] 가 겹치는지(경계 포함). */
    private boolean periodsOverlap(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }

    /** 병실 기준정보의 식별·기간 묶음(겹침 검사 전용). */
    record RoomConfigPeriod(String wardName, String roomName, LocalDate start, LocalDate end) {
    }

    private PreparedRoomConfig prepareRoomConfig(RoomBoardRoomConfig config) {
        String roomName = normalizeRoomName(config.getRoomName());
        String wardName = safeText(config.getWardName(), 50);
        if (wardName.isBlank() || roomName.isBlank()) {
            throw new IllegalArgumentException("병동과 병실은 필수입니다.");
        }
        LocalDate startDate = parseDate(config.getStartDate(), null);
        LocalDate endDate = parseDate(config.getEndDate(), null);
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("개시일자와 종료일자를 확인해 주세요.");
        }
        int licensedBeds = config.getLicensedBeds() == null ? 0 : Math.max(0, config.getLicensedBeds());
        if (licensedBeds <= 0) {
            throw new IllegalArgumentException("허가병상수는 1 이상이어야 합니다.");
        }
        int availableBeds = effectiveAvailableBeds(config.getAvailableBeds(), licensedBeds);
        return new PreparedRoomConfig(
                wardName,
                roomName,
                startDate,
                endDate,
                licensedBeds,
                availableBeds,
                normalizeRoomGender(config.getRoomGender()),
                safeText(config.getCareType(), 100),
                normalizeYn(config.getStatusWalk()),
                normalizeYn(config.getStatusDiaper()),
                normalizeYn(config.getStatusOxygen()),
                normalizeYn(config.getStatusSuction()),
                safeText(config.getNursingCost(), 100),
                safeText(config.getNote(), 500),
                normalizeYn(config.getUseYn()));
    }

    /**
     * Operational available beds (가용병상수): defaults to licensed beds when unset,
     * never negative, and never exceeds licensed beds.
     */
    int effectiveAvailableBeds(Integer availableBeds, int licensedBeds) {
        if (availableBeds == null) {
            return licensedBeds;
        }
        int value = Math.max(0, availableBeds);
        if (value <= 0) {
            return licensedBeds;
        }
        return Math.min(value, licensedBeds);
    }

    String normalizeRoomGender(String roomGender) {
        String value = safeText(roomGender, 10).replace(" ", "");
        switch (value.toUpperCase(Locale.ROOT)) {
            case "남", "남성", "M":
                return "남";
            case "여", "여성", "F":
                return "여";
            case "혼용", "공용", "혼합", "MIX", "MIXED":
                return "혼용";
            default:
                return "";
        }
    }

    private long insertRoomConfig(String inst, PreparedRoomConfig config, String username) {
        String safe = sanitizeInst(inst);
        String sql = """
                INSERT INTO csm.room_board_room_master_%s
                (ward_name, room_name, start_date, end_date, licensed_beds, available_beds, room_gender, care_type,
                 status_walk, status_diaper, status_oxygen, status_suction,
                 nursing_cost, note, use_yn, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, config.wardName());
            ps.setString(2, config.roomName());
            ps.setObject(3, config.startDate());
            ps.setObject(4, config.endDate());
            ps.setInt(5, config.licensedBeds());
            ps.setInt(6, config.availableBeds());
            ps.setString(7, config.roomGender());
            ps.setString(8, config.careType());
            ps.setString(9, config.statusWalk());
            ps.setString(10, config.statusDiaper());
            ps.setString(11, config.statusOxygen());
            ps.setString(12, config.statusSuction());
            ps.setString(13, config.nursingCost());
            ps.setString(14, config.note());
            ps.setString(15, config.useYn());
            ps.setString(16, safeText(username, 100));
            ps.setString(17, safeText(username, 100));
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private void updateRoomConfig(String inst, long id, PreparedRoomConfig config, String username) {
        String safe = sanitizeInst(inst);
        String sql = """
                UPDATE csm.room_board_room_master_%s
                   SET ward_name=?,
                       room_name=?,
                       start_date=?,
                       end_date=?,
                       licensed_beds=?,
                       available_beds=?,
                       room_gender=?,
                       care_type=?,
                       status_walk=?,
                       status_diaper=?,
                       status_oxygen=?,
                       status_suction=?,
                       nursing_cost=?,
                       note=?,
                       use_yn=?,
                       updated_by=?
                 WHERE rbm_id=?
                """.formatted(safe);
        jdbcTemplate.update(sql,
                config.wardName(),
                config.roomName(),
                config.startDate(),
                config.endDate(),
                config.licensedBeds(),
                config.availableBeds(),
                config.roomGender(),
                config.careType(),
                config.statusWalk(),
                config.statusDiaper(),
                config.statusOxygen(),
                config.statusSuction(),
                config.nursingCost(),
                config.note(),
                config.useYn(),
                safeText(username, 100),
                id);
    }

    private record PreparedRoomConfig(
            String wardName,
            String roomName,
            LocalDate startDate,
            LocalDate endDate,
            int licensedBeds,
            int availableBeds,
            String roomGender,
            String careType,
            String statusWalk,
            String statusDiaper,
            String statusOxygen,
            String statusSuction,
            String nursingCost,
            String note,
            String useYn) {
    }

    private record ParsedSlot(
            String wardName,
            String roomName,
            Integer bedNo) {
    }

    // ═══════════════════════════════════════════════════════════════════
    //  입원 예약 관리 (admission-reservation management)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * cs_col_19 = '입원예약' 인 상담 기록 목록을 반환합니다.
     * 환자명(cs_col_01)은 AES ECB 복호화하여 채웁니다.
     */
    public List<AdmissionReservationItem> listAdmissionReservations(String inst) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT cd.cs_idx,
                       cd.cs_col_01 AS patient_name_hex,
                       cd.cs_col_02 AS gender,
                       cd.cs_col_03 AS birth_date,
                       g.name             AS guardian_name,
                       g.contact_number   AS guardian_phone_hex,
                       cd.cs_col_16 AS counsel_date,
                       cd.cs_col_17 AS counselor,
                       cd.cs_col_19 AS status,
                       cd.cs_col_21 AS planned_date,
                       cd.cs_col_38 AS room_name
                  FROM csm.counsel_data_%s cd
                  LEFT JOIN (
                      SELECT cs_idx, name, contact_number
                        FROM csm.counsel_data_%s_guardians
                       WHERE id IN (SELECT MIN(id) FROM csm.counsel_data_%s_guardians GROUP BY cs_idx)
                  ) g ON g.cs_idx = cd.cs_idx
                 WHERE cd.cs_col_19 = '입원예약'
                 ORDER BY cd.cs_col_16 DESC, cd.cs_idx DESC
                """.formatted(safe, safe, safe);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<AdmissionReservationItem> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            AdmissionReservationItem item = new AdmissionReservationItem();
            item.setCsIdx(toLong(row.get("cs_idx")));
            item.setPatientName(decryptHexString(safeText(row.get("patient_name_hex"), 500)));
            item.setGender(safeText(row.get("gender"), 20));
            item.setBirthDate(safeText(row.get("birth_date"), 20));
            item.setGuardianName(decryptHexString(safeText(row.get("guardian_name"), 100)));
            item.setGuardianPhone(decryptHexString(safeText(row.get("guardian_phone_hex"), 500)));
            item.setCounselDate(safeText(row.get("counsel_date"), 20));
            item.setCounselor(safeText(row.get("counselor"), 100));
            item.setStatus(safeText(row.get("status"), 50));
            item.setPlannedDate(safeText(row.get("planned_date"), 20));
            item.setRoomName(safeText(row.get("room_name"), 100));
            result.add(item);
        }
        return result;
    }

    /**
     * 현재 활성 병실 목록을 반환합니다 (병동명 + 병실명 조합).
     */
    public List<Map<String, String>> listAvailableRooms(String inst) {
        String safe = sanitizeInst(inst);
        try {
            String sql = """
                    SELECT DISTINCT ward_name, room_name
                      FROM csm.room_board_room_master_%s
                     WHERE use_yn = 'Y'
                     ORDER BY ward_name ASC, room_name ASC
                    """.formatted(safe);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, String> map = new java.util.LinkedHashMap<>();
                map.put("wardName", rs.getString("ward_name"));
                map.put("roomName", rs.getString("room_name"));
                return map;
            });
        } catch (Exception e) {
            log.debug("[admission-res] room list fail inst={}: {}", inst, e.toString());
            return new ArrayList<>();
        }
    }

    /**
     * 입원예정일 및 배정병실을 업데이트합니다.
     * @return 업데이트된 행 수
     */
    @Transactional
    public int updateAdmissionDetails(String inst, long csIdx, String plannedDate, String roomName) {
        String safe = sanitizeInst(inst);
        String safePlannedDate = plannedDate == null ? "" : plannedDate.trim();
        String safeRoomName = roomName == null ? "" : roomName.trim();
        int rows = jdbcTemplate.update(
                "UPDATE csm.counsel_data_" + safe + " SET cs_col_21 = ?, cs_col_38 = ?, updated_at = NOW() WHERE cs_idx = ?",
                safePlannedDate.isEmpty() ? null : safePlannedDate,
                safeRoomName.isEmpty() ? null : safeRoomName,
                csIdx);
        if (rows == 0) {
            throw new IllegalArgumentException("업데이트할 상담 기록을 찾을 수 없습니다. csIdx=" + csIdx);
        }
        return rows;
    }

    /**
     * 상담결과를 '입원완료'로 변경합니다.
     */
    @Transactional
    public void confirmAdmission(String inst, long csIdx) {
        confirmAdmission(inst, csIdx, null, null);
    }

    /**
     * 상담결과를 '입원완료'로 변경하고 병실현황판 최신 스냅샷에 재원자로 반영합니다.
     */
    @Transactional
    public void confirmAdmission(String inst, long csIdx, String plannedDate, String roomName) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        String safePlannedDate = plannedDate == null ? "" : plannedDate.trim();
        String safeRoomName = roomName == null ? "" : roomName.trim();
        if (!safePlannedDate.isBlank() || !safeRoomName.isBlank()) {
            updateAdmissionDetails(safe, csIdx, safePlannedDate, safeRoomName);
        }

        Map<String, Object> admission = findAdmissionReservationForBoard(safe, csIdx);
        String assignedRoom = normalizeRoomName(firstNonBlank(safeRoomName, safeText(admission.get("room_name"), 100)));
        if (assignedRoom.isBlank()) {
            throw new IllegalArgumentException("병실을 먼저 선택해주세요.");
        }

        jdbcTemplate.update(
                "UPDATE csm.counsel_data_" + safe + " SET cs_col_19 = '입원완료', updated_at = NOW() WHERE cs_idx = ?",
                csIdx);
        addConfirmedAdmissionToCurrentSnapshot(safe, admission, assignedRoom);
    }

    private Map<String, Object> findAdmissionReservationForBoard(String inst, long csIdx) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT cs_idx, cs_col_01 AS patient_name_hex, cs_col_02 AS gender,
                       cs_col_21 AS planned_date, cs_col_38 AS room_name
                  FROM csm.counsel_data_%s
                 WHERE cs_idx = ?
                 LIMIT 1
                """.formatted(safe);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, csIdx);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("입원예약 정보를 찾을 수 없습니다.");
        }
        return rows.get(0);
    }

    private void addConfirmedAdmissionToCurrentSnapshot(String inst, Map<String, Object> admission, String roomName) {
        String safe = sanitizeInst(inst);
        RoomBoardSnapshot snapshot = findSnapshot(safe, null);
        long snapshotId = snapshot == null || snapshot.getId() == null
                ? createAdmissionReservationSnapshot(safe)
                : snapshot.getId();
        String patientName = decryptHexString(safeText(admission.get("patient_name_hex"), 500));
        if (patientName.isBlank()) {
            patientName = "입원완료환자";
        }
        if (isPatientAlreadyInSnapshot(safe, snapshotId, roomName, patientName)) {
            return;
        }
        String wardName = findWardNameByRoom(safe, roomName);
        String plannedDate = safeText(admission.get("planned_date"), 20);
        jdbcTemplate.update("""
                INSERT INTO csm.room_board_patient_%s
                (rbs_id, ward_name, room_name, patient_name, gender, admission_date, patient_type, memo, raw_row)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe),
                snapshotId,
                wardName.isBlank() ? null : wardName,
                roomName,
                patientName,
                normalizeGender(safeText(admission.get("gender"), 20)),
                plannedDate.isBlank() ? LocalDate.now().format(DATE_FMT) : plannedDate,
                "입원완료",
                "입원예약관리에서 입원완료 처리",
                "admission-reservation:" + safeText(admission.get("cs_idx"), 30));
    }

    private long createAdmissionReservationSnapshot(String inst) {
        String safe = sanitizeInst(inst);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO csm.room_board_snapshot_%s
                    (source_type, snapshot_date, snapshot_time, raw_text, uploaded_by, parse_status, parse_message)
                    VALUES ('ADMISSION_RESERVATION', ?, ?, '', 'admission-reservation', 'SUCCESS', '입원예약 입원완료 처리')
                    """.formatted(safe), Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, LocalDate.now());
            ps.setString(2, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private boolean isPatientAlreadyInSnapshot(String inst, long snapshotId, String roomName, String patientName) {
        String safe = sanitizeInst(inst);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM csm.room_board_patient_%s
                 WHERE rbs_id = ?
                   AND room_name = ?
                   AND patient_name = ?
                """.formatted(safe), Integer.class, snapshotId, roomName, patientName);
        return count != null && count > 0;
    }

    private String findWardNameByRoom(String inst, String roomName) {
        String safe = sanitizeInst(inst);
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT ward_name
                 FROM csm.room_board_room_master_%s
                 WHERE use_yn = 'Y'
                   AND REPLACE(room_name, ' ', '') = ?
                   AND start_date <= ?
                   AND end_date >= ?
                 ORDER BY start_date DESC, rbm_id DESC
                 LIMIT 1
                """.formatted(safe), String.class, roomName, LocalDate.now(), LocalDate.now());
        return rows.isEmpty() ? "" : safeText(rows.get(0), 50);
    }

    /**
     * 입원예약을 취소하고 지정된 상태로 되돌립니다.
     */
    @Transactional
    public void cancelAdmissionReservation(String inst, long csIdx, String revertStatus) {
        String safe = sanitizeInst(inst);
        String status = (revertStatus == null || revertStatus.isBlank()) ? "상담중" : revertStatus.trim();
        jdbcTemplate.update(
                "UPDATE csm.counsel_data_" + safe
                + " SET cs_col_19 = ?, cs_col_38 = NULL, cs_col_21 = NULL, updated_at = NOW() WHERE cs_idx = ?",
                status, csIdx);
    }

    private Map<String, Object> findCurrentPatientForDischarge(String safe, Long rbpId) {
        if (rbpId == null || rbpId <= 0) {
            return null;
        }
        // rbp_id is a primary key — no rbs_id constraint so discharge notices can be
        // registered even after a new snapshot is imported on top of a patient that
        // was originally added via confirmAdmission (addConfirmedAdmissionToCurrentSnapshot).
        String sql = """
                SELECT rbp_id, rbs_id, ward_name, room_name, patient_no, patient_name
                  FROM csm.room_board_patient_%s
                 WHERE rbp_id = ?
                 LIMIT 1
                """.formatted(safe);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, rbpId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String normalizeDischargeTime(String value) {
        String raw = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return "PM".equals(raw) ? "PM" : "AM";
    }

    private String dischargeTimeLabel(String value) {
        return "PM".equalsIgnoreCase(value) ? "오후" : "오전";
    }

    private String normalizeDischargeStatus(String value) {
        String raw = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(raw) || "CANCELLED".equals(raw)) {
            return raw;
        }
        return "PLANNED";
    }

    private String dischargeStatusLabel(String value) {
        return switch (normalizeDischargeStatus(value)) {
            case "COMPLETED" -> "퇴원완료";
            case "CANCELLED" -> "취소";
            default -> "퇴원예정";
        };
    }

    private String dischargeAvailabilityLabel(String status, String dischargeTime) {
        if ("CANCELLED".equalsIgnoreCase(status)) {
            return "병상 변동 없음";
        }
        if ("COMPLETED".equalsIgnoreCase(status)) {
            return "입원 가능";
        }
        return "AM".equalsIgnoreCase(dischargeTime) ? "오후 입원 가능" : "익일 입원 권장";
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString().trim()); } catch (Exception e) { return 0L; }
    }
}
