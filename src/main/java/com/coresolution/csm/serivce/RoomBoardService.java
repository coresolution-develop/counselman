package com.coresolution.csm.serivce;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.vo.RoomBoardImportResult;
import com.coresolution.csm.vo.RoomBoardImportRow;
import com.coresolution.csm.vo.RoomBoardRoomConfig;
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
    private static final String AES_KEY = "This is key!!!!!";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final JdbcTemplate jdbcTemplate;

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
    }

    public List<RoomBoardRoomConfig> getRoomConfigs(String inst) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbm_id, ward_name, room_name, DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date, licensed_beds, care_type,
                       status_walk, status_diaper, status_oxygen, status_suction,
                       nursing_cost, note, use_yn,
                       DATE_FORMAT(created_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS created_at,
                       created_by,
                       DATE_FORMAT(updated_at,'%%Y-%%m-%%d %%H:%%i:%%s') AS updated_at,
                       updated_by
                  FROM csm.room_board_room_master_%s
                 ORDER BY start_date DESC, ward_name ASC, room_name ASC, rbm_id DESC
                """.formatted(safe);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            RoomBoardRoomConfig item = new RoomBoardRoomConfig();
            item.setId(rs.getLong("rbm_id"));
            item.setWardName(rs.getString("ward_name"));
            item.setRoomName(rs.getString("room_name"));
            item.setStartDate(rs.getString("start_date"));
            item.setEndDate(rs.getString("end_date"));
            item.setLicensedBeds(rs.getInt("licensed_beds"));
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
        });
    }

    @Transactional
    public void saveRoomConfig(String inst, RoomBoardRoomConfig config, String username) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        long id = config.getId() == null ? 0L : config.getId();
        String roomName = normalizeRoomName(config.getRoomName());
        String wardName = safeText(config.getWardName(), 50);
        if (wardName.isBlank() || roomName.isBlank()) {
            throw new IllegalArgumentException("병동과 병실은 필수입니다.");
        }
        LocalDate startDate = parseDate(config.getStartDate(), LocalDate.now());
        LocalDate endDate = parseDate(config.getEndDate(), LocalDate.of(9999, 12, 31));
        int licensedBeds = config.getLicensedBeds() == null ? 0 : Math.max(0, config.getLicensedBeds());
        if (licensedBeds <= 0) {
            throw new IllegalArgumentException("허가병상수는 1 이상이어야 합니다.");
        }
        if (id > 0) {
            String sql = """
                    UPDATE csm.room_board_room_master_%s
                       SET ward_name=?,
                           room_name=?,
                           start_date=?,
                           end_date=?,
                           licensed_beds=?,
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
                    wardName,
                    roomName,
                    startDate,
                    endDate,
                    licensedBeds,
                    safeText(config.getCareType(), 100),
                    normalizeYn(config.getStatusWalk()),
                    normalizeYn(config.getStatusDiaper()),
                    normalizeYn(config.getStatusOxygen()),
                    normalizeYn(config.getStatusSuction()),
                    safeText(config.getNursingCost(), 100),
                    safeText(config.getNote(), 500),
                    normalizeYn(config.getUseYn()),
                    safeText(username, 100),
                    id);
            return;
        }
        String sql = """
                INSERT INTO csm.room_board_room_master_%s
                (ward_name, room_name, start_date, end_date, licensed_beds, care_type,
                 status_walk, status_diaper, status_oxygen, status_suction,
                 nursing_cost, note, use_yn, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe);
        jdbcTemplate.update(sql,
                wardName,
                roomName,
                startDate,
                endDate,
                licensedBeds,
                safeText(config.getCareType(), 100),
                normalizeYn(config.getStatusWalk()),
                normalizeYn(config.getStatusDiaper()),
                normalizeYn(config.getStatusOxygen()),
                normalizeYn(config.getStatusSuction()),
                safeText(config.getNursingCost(), 100),
                safeText(config.getNote(), 500),
                normalizeYn(config.getUseYn()),
                safeText(username, 100),
                safeText(username, 100));
    }

    public void deleteRoomConfig(String inst, long id) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        jdbcTemplate.update("DELETE FROM csm.room_board_room_master_" + safe + " WHERE rbm_id = ?", id);
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

        List<RoomBoardImportRow> parsed = "CLICKSOFT".equalsIgnoreCase(sourceType)
                ? parseClicksoftRows(rows)
                : parseExcelDetailRows(rows, wardByRoom);

        parsed = parsed.stream()
                .filter(row -> !safeText(row.getRoomName(), 50).isBlank())
                .filter(row -> !safeText(row.getPatientName(), 100).isBlank())
                .collect(Collectors.toList());

        result.setParsedCount(parsed.size());
        result.setRows(parsed);
        result.setSkippedCount(Math.max(0, rows.size() - parsed.size()));
        result.setMessage(parsed.isEmpty() ? "인식된 환자 데이터가 없습니다." : "미리보기 생성 완료");
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
                (rbs_id, ward_name, room_name, patient_no, patient_name, gender, age, admission_date,
                 doctor_name, patient_type, disease_name, disease_code, phone_patient, phone_guardian, memo, raw_row)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(safe);
        for (RoomBoardImportRow row : preview.getRows()) {
            jdbcTemplate.update(insertSql,
                    snapshotId,
                    safeText(row.getWardName(), 50),
                    normalizeRoomName(row.getRoomName()),
                    safeText(row.getPatientNo(), 100),
                    safeText(row.getPatientName(), 100),
                    normalizeGender(row.getGender()),
                    safeText(row.getAge(), 20),
                    safeText(row.getAdmissionDate(), 20),
                    safeText(row.getDoctorName(), 100),
                    safeText(row.getPatientType(), 50),
                    null,
                    null,
                    null,
                    null,
                    safeText(row.getMemo(), 1000),
                    buildRawRow(row));
        }
        return preview;
    }

    public RoomBoardView getBoard(String inst, String snapshotDate) {
        ensureTables(inst);
        String safe = sanitizeInst(inst);
        RoomBoardSnapshot snapshot = findSnapshot(safe, snapshotDate);
        LocalDate baseDate = snapshot != null && snapshot.getSnapshotDate() != null
                ? parseDate(snapshot.getSnapshotDate(), LocalDate.now())
                : parseDate(snapshotDate, LocalDate.now());
        List<RoomBoardRoomConfig> activeConfigs = getActiveRoomConfigs(safe, baseDate);
        List<Map<String, Object>> patients = snapshot == null ? List.of() : loadSnapshotPatients(safe, snapshot.getId());
        Map<String, List<Map<String, Object>>> patientsByRoom = patients.stream()
                .collect(Collectors.groupingBy(row -> normalizeRoomName(String.valueOf(row.get("room_name")))));
        Map<String, List<String>> reservationNamesByRoom = loadReservationNamesByRoom(safe, baseDate);

        Map<String, RoomBoardWardView> wardMap = new LinkedHashMap<>();
        int totalLicensed = 0;
        int totalOccupied = 0;
        int totalAvailable = 0;

        for (RoomBoardRoomConfig config : activeConfigs) {
            String wardName = safeText(config.getWardName(), 50);
            String roomName = normalizeRoomName(config.getRoomName());
            List<Map<String, Object>> roomPatients = patientsByRoom.getOrDefault(roomName, List.of());
            List<String> reservationNames = reservationNamesByRoom.getOrDefault(roomName, List.of());
            int occupiedCount = roomPatients.size();
            int licensedBeds = config.getLicensedBeds() == null ? 0 : config.getLicensedBeds();
            int availableCount = Math.max(licensedBeds - occupiedCount - reservationNames.size(), 0);

            RoomBoardRoomView room = new RoomBoardRoomView();
            room.setWardName(wardName);
            room.setRoomName(roomName);
            room.setLicensedBeds(licensedBeds);
            room.setOccupiedCount(occupiedCount);
            room.setReservationCount(reservationNames.size());
            room.setAvailableCount(availableCount);
            room.setOccupancyRate(licensedBeds <= 0 ? 0d : round1(occupiedCount * 100d / licensedBeds));
            room.setCareType(safeText(config.getCareType(), 100));
            room.setStatusLabel(config.getStatusLabel());
            room.setNote(safeText(config.getNote(), 500));
            room.setGenderSummary(buildGenderSummary(roomPatients));
            room.setReservationNames(String.join(", ", reservationNames));
            room.setPatientSlots(buildPatientSlots(roomPatients, Math.max(licensedBeds, 8)));

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

    private List<Map<String, Object>> loadSnapshotPatients(String inst, Long snapshotId) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT room_name, ward_name, patient_name, gender
                  FROM csm.room_board_patient_%s
                 WHERE rbs_id = ?
                 ORDER BY room_name ASC, patient_name ASC
                """.formatted(safe);
        return jdbcTemplate.queryForList(sql, snapshotId);
    }

    private List<RoomBoardRoomConfig> getActiveRoomConfigs(String inst, LocalDate baseDate) {
        String safe = sanitizeInst(inst);
        String sql = """
                SELECT rbm_id, ward_name, room_name, DATE_FORMAT(start_date,'%%Y-%%m-%%d') AS start_date,
                       DATE_FORMAT(end_date,'%%Y-%%m-%%d') AS end_date, licensed_beds, care_type,
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
                SELECT cs_idx, HEX(cs_col_01) AS patient_name_hex, cs_col_38, cs_col_21
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

    private List<String> buildPatientSlots(List<Map<String, Object>> roomPatients, int size) {
        List<String> slots = roomPatients.stream()
                .map(row -> safeText(row.get("patient_name"), 100))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
        while (slots.size() < size) {
            slots.add("-");
        }
        return slots;
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
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\t", -1))
                .collect(Collectors.toList());
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
            String roomName = normalizeRoomName(firstNonBlank(getCell(row, 1), getCell(row, 3)));
            String patientName = safeText(getCell(row, 6), 100);
            if (roomName.isBlank() || patientName.isBlank()) {
                continue;
            }
            RoomBoardImportRow item = new RoomBoardImportRow();
            item.setWardName(wardName);
            item.setRoomName(roomName);
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
            SecretKeySpec secretKey = new SecretKeySpec(Arrays.copyOf(AES_KEY.getBytes(StandardCharsets.UTF_8), 16),
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
}
