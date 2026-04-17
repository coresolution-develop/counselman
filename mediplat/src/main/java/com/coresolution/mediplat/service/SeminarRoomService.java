package com.coresolution.mediplat.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.SeminarNotification;
import com.coresolution.mediplat.model.SeminarReservation;
import com.coresolution.mediplat.model.SeminarRoom;

import jakarta.annotation.PostConstruct;

@Service
public class SeminarRoomService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String USE_Y = "Y";

    private final JdbcTemplate jdbcTemplate;
    private DatabaseDialect databaseDialect = DatabaseDialect.H2;

    public SeminarRoomService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        databaseDialect = detectDatabaseDialect();
        createTables();
    }

    public List<SeminarRoom> listSeminars(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, inst_code, seminar_name, room_name, capacity, use_yn
                FROM mp_seminar_room
                WHERE inst_code = ?
                ORDER BY use_yn DESC, seminar_name ASC, id ASC
                """, (rs, rowNum) -> new SeminarRoom(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("seminar_name"),
                        rs.getString("room_name"),
                        rs.getInt("capacity"),
                        rs.getString("use_yn")),
                normalizedInstCode);
    }

    public SeminarRoom findSeminar(String instCode, Long seminarId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || seminarId == null) {
            return null;
        }
        List<SeminarRoom> result = jdbcTemplate.query("""
                SELECT id, inst_code, seminar_name, room_name, capacity, use_yn
                FROM mp_seminar_room
                WHERE inst_code = ?
                  AND id = ?
                LIMIT 1
                """, (rs, rowNum) -> new SeminarRoom(
                        rs.getLong("id"),
                        rs.getString("inst_code"),
                        rs.getString("seminar_name"),
                        rs.getString("room_name"),
                        rs.getInt("capacity"),
                        rs.getString("use_yn")),
                normalizedInstCode,
                seminarId);
        return result.isEmpty() ? null : result.get(0);
    }

    public void saveSeminar(
            String instCode,
            String seminarName,
            String roomName,
            Integer capacity,
            String useYn,
            String createdBy) {
        saveSeminar(instCode, null, seminarName, roomName, capacity, useYn, createdBy);
    }

    public void saveSeminar(
            String instCode,
            Long seminarId,
            String seminarName,
            String roomName,
            Integer capacity,
            String useYn,
            String createdBy) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedSeminarName = trimToNull(seminarName);
        String normalizedRoomName = trimToNull(roomName);
        Integer normalizedCapacity = capacity == null ? 1 : capacity;
        if (!StringUtils.hasText(normalizedInstCode)
                || !StringUtils.hasText(normalizedSeminarName)
                || !StringUtils.hasText(normalizedRoomName)) {
            throw new IllegalArgumentException("세미나명, 세미나실명은 필수입니다.");
        }
        if (normalizedCapacity <= 0 || normalizedCapacity > 1000) {
            throw new IllegalArgumentException("수용 인원은 1~1000 사이로 입력해 주세요.");
        }
        String normalizedUseYn = normalizeYn(useYn);
        if (seminarId != null) {
            updateSeminar(
                    normalizedInstCode,
                    seminarId,
                    normalizedSeminarName,
                    normalizedRoomName,
                    normalizedCapacity,
                    normalizedUseYn);
            return;
        }
        upsertSeminar(
                normalizedInstCode,
                normalizedSeminarName,
                normalizedRoomName,
                normalizedCapacity,
                normalizedUseYn,
                trimToNull(createdBy));
    }

    public List<String> listManagerUsernames(String instCode, Long seminarId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || seminarId == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT username
                FROM mp_seminar_manager
                WHERE inst_code = ?
                  AND seminar_id = ?
                  AND use_yn = 'Y'
                ORDER BY username ASC
                """, (rs, rowNum) -> rs.getString("username"),
                normalizedInstCode,
                seminarId);
    }

    public void saveSeminarManagers(String instCode, Long seminarId, List<String> managerUsernames) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || seminarId == null) {
            throw new IllegalArgumentException("세미나 관리 대상을 확인해 주세요.");
        }
        SeminarRoom seminar = findSeminar(normalizedInstCode, seminarId);
        if (seminar == null) {
            throw new IllegalArgumentException("등록된 세미나를 찾을 수 없습니다.");
        }
        List<String> normalizedManagerUsernames = new ArrayList<>();
        if (managerUsernames != null) {
            managerUsernames.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .forEach(normalizedManagerUsernames::add);
        }
        jdbcTemplate.update("""
                DELETE FROM mp_seminar_manager
                WHERE inst_code = ?
                  AND seminar_id = ?
                """, normalizedInstCode, seminarId);
        for (String username : normalizedManagerUsernames) {
            jdbcTemplate.update("""
                    INSERT INTO mp_seminar_manager (inst_code, seminar_id, username, use_yn, created_at)
                    VALUES (?, ?, ?, 'Y', CURRENT_TIMESTAMP)
                    """,
                    normalizedInstCode,
                    seminarId,
                    username);
        }
    }

    public boolean isSeminarManager(String instCode, String username) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_seminar_manager m
                JOIN mp_seminar_room s
                  ON s.id = m.seminar_id
                 AND s.inst_code = m.inst_code
                WHERE m.inst_code = ?
                  AND LOWER(m.username) = LOWER(?)
                  AND m.use_yn = 'Y'
                  AND s.use_yn = 'Y'
                """, Integer.class, normalizedInstCode, normalizedUsername);
        return count != null && count > 0;
    }

    public SeminarReservation createReservation(
            String instCode,
            Long seminarId,
            String requesterUsername,
            String requesterName,
            LocalDate reservationDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer attendeeCount,
            String usedItems,
            String neededItems,
            String purpose) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(requesterUsername);
        String normalizedRequesterName = StringUtils.hasText(requesterName) ? requesterName.trim() : normalizedUsername;
        if (!StringUtils.hasText(normalizedInstCode) || seminarId == null || !StringUtils.hasText(normalizedUsername)) {
            throw new IllegalArgumentException("예약 정보를 확인해 주세요.");
        }
        SeminarRoom seminar = findSeminar(normalizedInstCode, seminarId);
        if (seminar == null || !seminar.isEnabled()) {
            throw new IllegalArgumentException("예약 가능한 세미나를 찾을 수 없습니다.");
        }
        if (reservationDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("예약 날짜와 시간을 입력해 주세요.");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
        int normalizedAttendeeCount = attendeeCount == null ? 1 : attendeeCount;
        if (normalizedAttendeeCount <= 0) {
            throw new IllegalArgumentException("인원은 1명 이상이어야 합니다.");
        }
        if (seminar.getCapacity() != null && normalizedAttendeeCount > seminar.getCapacity()) {
            throw new IllegalArgumentException("인원이 수용 인원을 초과했습니다.");
        }
        if (hasOverlappingReservation(normalizedInstCode, seminarId, reservationDate, startTime, endTime)) {
            throw new IllegalArgumentException("해당 시간대에는 이미 예약이 있습니다.");
        }

        jdbcTemplate.update("""
                INSERT INTO mp_seminar_reservation (
                    inst_code, seminar_id, requester_username, requester_name,
                    reservation_date, start_time, end_time, attendee_count,
                    used_items, needed_items, purpose, status_code, manager_note, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                normalizedInstCode,
                seminarId,
                normalizedUsername,
                normalizedRequesterName,
                java.sql.Date.valueOf(reservationDate),
                java.sql.Time.valueOf(startTime),
                java.sql.Time.valueOf(endTime),
                normalizedAttendeeCount,
                trimToNull(usedItems),
                trimToNull(neededItems),
                trimToNull(purpose),
                STATUS_PENDING);

        SeminarReservation createdReservation = findLatestReservation(normalizedInstCode, seminarId, normalizedUsername, reservationDate, startTime, endTime);
        if (createdReservation != null) {
            createManagerNotifications(normalizedInstCode, createdReservation);
        }
        return createdReservation;
    }

    public List<SeminarReservation> listMyReservations(String instCode, String username) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT r.id, r.inst_code, r.seminar_id, s.seminar_name,
                       r.requester_username, r.requester_name,
                       r.reservation_date, r.start_time, r.end_time, r.attendee_count,
                       r.used_items, r.needed_items, r.purpose, r.status_code, r.manager_note, r.created_at
                FROM mp_seminar_reservation r
                JOIN mp_seminar_room s
                  ON s.id = r.seminar_id
                 AND s.inst_code = r.inst_code
                WHERE r.inst_code = ?
                  AND LOWER(r.requester_username) = LOWER(?)
                ORDER BY r.reservation_date DESC, r.start_time DESC, r.id DESC
                """, (rs, rowNum) -> mapReservation(rs),
                normalizedInstCode,
                normalizedUsername);
    }

    public List<SeminarReservation> listReservationsForCalendar(
            String instCode,
            LocalDate fromDate,
            LocalDate toDate) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || fromDate == null || toDate == null) {
            return List.of();
        }
        if (toDate.isBefore(fromDate)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT r.id, r.inst_code, r.seminar_id, s.seminar_name,
                       r.requester_username, r.requester_name,
                       r.reservation_date, r.start_time, r.end_time, r.attendee_count,
                       r.used_items, r.needed_items, r.purpose, r.status_code, r.manager_note, r.created_at
                FROM mp_seminar_reservation r
                JOIN mp_seminar_room s
                  ON s.id = r.seminar_id
                 AND s.inst_code = r.inst_code
                WHERE r.inst_code = ?
                  AND r.reservation_date BETWEEN ? AND ?
                ORDER BY r.reservation_date ASC, r.start_time ASC, r.id ASC
                """, (rs, rowNum) -> mapReservation(rs),
                normalizedInstCode,
                java.sql.Date.valueOf(fromDate),
                java.sql.Date.valueOf(toDate));
    }

    public List<SeminarReservation> listPendingReservationsForManager(String instCode, String managerUsername) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(managerUsername);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT r.id, r.inst_code, r.seminar_id, s.seminar_name,
                       r.requester_username, r.requester_name,
                       r.reservation_date, r.start_time, r.end_time, r.attendee_count,
                       r.used_items, r.needed_items, r.purpose, r.status_code, r.manager_note, r.created_at
                FROM mp_seminar_reservation r
                JOIN mp_seminar_room s
                  ON s.id = r.seminar_id
                 AND s.inst_code = r.inst_code
                JOIN mp_seminar_manager m
                  ON m.inst_code = r.inst_code
                 AND m.seminar_id = r.seminar_id
                 AND LOWER(m.username) = LOWER(?)
                 AND m.use_yn = 'Y'
                WHERE r.inst_code = ?
                  AND r.status_code = ?
                ORDER BY r.reservation_date ASC, r.start_time ASC, r.id ASC
                """, (rs, rowNum) -> mapReservation(rs),
                normalizedUsername,
                normalizedInstCode,
                STATUS_PENDING);
    }

    public void decideReservation(
            String instCode,
            Long reservationId,
            String managerUsername,
            String decision,
            String managerNote) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedManagerUsername = normalizeUsername(managerUsername);
        if (!StringUtils.hasText(normalizedInstCode) || reservationId == null || !StringUtils.hasText(normalizedManagerUsername)) {
            throw new IllegalArgumentException("승인 정보를 확인해 주세요.");
        }
        SeminarReservation reservation = findReservation(normalizedInstCode, reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("예약 정보를 찾을 수 없습니다.");
        }
        if (!reservation.isPending()) {
            throw new IllegalArgumentException("대기 상태의 예약만 처리할 수 있습니다.");
        }
        if (!isSeminarManagerForSeminar(normalizedInstCode, reservation.getSeminarId(), normalizedManagerUsername)) {
            throw new IllegalArgumentException("해당 세미나 예약을 처리할 권한이 없습니다.");
        }
        String normalizedDecision = STATUS_REJECTED.equalsIgnoreCase(decision) ? STATUS_REJECTED : STATUS_APPROVED;
        jdbcTemplate.update("""
                UPDATE mp_seminar_reservation
                SET status_code = ?,
                    manager_note = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE inst_code = ?
                  AND id = ?
                """,
                normalizedDecision,
                trimToNull(managerNote),
                normalizedInstCode,
                reservationId);
        jdbcTemplate.update("""
                UPDATE mp_seminar_notification
                SET read_yn = 'Y'
                WHERE inst_code = ?
                  AND reservation_id = ?
                  AND LOWER(manager_username) = LOWER(?)
                """,
                normalizedInstCode,
                reservationId,
                normalizedManagerUsername);
    }

    public List<SeminarNotification> listNotifications(String instCode, String managerUsername) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(managerUsername);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, reservation_id, inst_code, manager_username, message, read_yn, created_at
                FROM mp_seminar_notification
                WHERE inst_code = ?
                  AND LOWER(manager_username) = LOWER(?)
                ORDER BY id DESC
                LIMIT 100
                """, (rs, rowNum) -> new SeminarNotification(
                        rs.getLong("id"),
                        rs.getLong("reservation_id"),
                        rs.getString("inst_code"),
                        rs.getString("manager_username"),
                        rs.getString("message"),
                        rs.getString("read_yn"),
                        toLocalDateTime(rs.getTimestamp("created_at"))),
                normalizedInstCode,
                normalizedUsername);
    }

    public int countUnreadNotifications(String instCode, String managerUsername) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(managerUsername);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_seminar_notification
                WHERE inst_code = ?
                  AND LOWER(manager_username) = LOWER(?)
                  AND read_yn = 'N'
                """, Integer.class,
                normalizedInstCode,
                normalizedUsername);
        return count == null ? 0 : count;
    }

    public void markNotificationsRead(String instCode, String managerUsername) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedUsername = normalizeUsername(managerUsername);
        if (!StringUtils.hasText(normalizedInstCode) || !StringUtils.hasText(normalizedUsername)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE mp_seminar_notification
                SET read_yn = 'Y'
                WHERE inst_code = ?
                  AND LOWER(manager_username) = LOWER(?)
                  AND read_yn = 'N'
                """, normalizedInstCode, normalizedUsername);
    }

    private SeminarReservation findLatestReservation(
            String instCode,
            Long seminarId,
            String requesterUsername,
            LocalDate reservationDate,
            LocalTime startTime,
            LocalTime endTime) {
        List<SeminarReservation> reservations = jdbcTemplate.query("""
                SELECT r.id, r.inst_code, r.seminar_id, s.seminar_name,
                       r.requester_username, r.requester_name,
                       r.reservation_date, r.start_time, r.end_time, r.attendee_count,
                       r.used_items, r.needed_items, r.purpose, r.status_code, r.manager_note, r.created_at
                FROM mp_seminar_reservation r
                JOIN mp_seminar_room s
                  ON s.id = r.seminar_id
                 AND s.inst_code = r.inst_code
                WHERE r.inst_code = ?
                  AND r.seminar_id = ?
                  AND LOWER(r.requester_username) = LOWER(?)
                  AND r.reservation_date = ?
                  AND r.start_time = ?
                  AND r.end_time = ?
                ORDER BY r.id DESC
                LIMIT 1
                """, (rs, rowNum) -> mapReservation(rs),
                instCode,
                seminarId,
                requesterUsername,
                java.sql.Date.valueOf(reservationDate),
                java.sql.Time.valueOf(startTime),
                java.sql.Time.valueOf(endTime));
        return reservations.isEmpty() ? null : reservations.get(0);
    }

    private SeminarReservation findReservation(String instCode, Long reservationId) {
        List<SeminarReservation> reservations = jdbcTemplate.query("""
                SELECT r.id, r.inst_code, r.seminar_id, s.seminar_name,
                       r.requester_username, r.requester_name,
                       r.reservation_date, r.start_time, r.end_time, r.attendee_count,
                       r.used_items, r.needed_items, r.purpose, r.status_code, r.manager_note, r.created_at
                FROM mp_seminar_reservation r
                JOIN mp_seminar_room s
                  ON s.id = r.seminar_id
                 AND s.inst_code = r.inst_code
                WHERE r.inst_code = ?
                  AND r.id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapReservation(rs),
                instCode,
                reservationId);
        return reservations.isEmpty() ? null : reservations.get(0);
    }

    private boolean hasOverlappingReservation(
            String instCode,
            Long seminarId,
            LocalDate reservationDate,
            LocalTime startTime,
            LocalTime endTime) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_seminar_reservation
                WHERE inst_code = ?
                  AND seminar_id = ?
                  AND reservation_date = ?
                  AND status_code IN ('PENDING', 'APPROVED')
                  AND NOT (end_time <= ? OR start_time >= ?)
                """, Integer.class,
                instCode,
                seminarId,
                java.sql.Date.valueOf(reservationDate),
                java.sql.Time.valueOf(startTime),
                java.sql.Time.valueOf(endTime));
        return count != null && count > 0;
    }

    private void createManagerNotifications(String instCode, SeminarReservation reservation) {
        if (reservation == null) {
            return;
        }
        List<String> managerUsernames = listManagerUsernames(instCode, reservation.getSeminarId());
        if (managerUsernames.isEmpty()) {
            return;
        }
        String reservationSlot = reservation.getReservationDate() + " "
                + reservation.getStartTime() + "~" + reservation.getEndTime();
        String message = reservation.getSeminarName() + " 예약 신청: " + reservationSlot
                + " / 신청자 " + reservation.getRequesterName()
                + " / 인원 " + reservation.getAttendeeCount() + "명";
        for (String managerUsername : managerUsernames) {
            jdbcTemplate.update("""
                    INSERT INTO mp_seminar_notification (
                        reservation_id, inst_code, manager_username, message, read_yn, created_at
                    ) VALUES (?, ?, ?, ?, 'N', CURRENT_TIMESTAMP)
                    """,
                    reservation.getId(),
                    instCode,
                    managerUsername,
                    message);
        }
    }

    private boolean isSeminarManagerForSeminar(String instCode, Long seminarId, String username) {
        if (!StringUtils.hasText(instCode) || seminarId == null || !StringUtils.hasText(username)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_seminar_manager
                WHERE inst_code = ?
                  AND seminar_id = ?
                  AND LOWER(username) = LOWER(?)
                  AND use_yn = 'Y'
                """, Integer.class,
                instCode,
                seminarId,
                username);
        return count != null && count > 0;
    }

    private SeminarReservation mapReservation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SeminarReservation(
                rs.getLong("id"),
                rs.getString("inst_code"),
                rs.getLong("seminar_id"),
                rs.getString("seminar_name"),
                rs.getString("requester_username"),
                rs.getString("requester_name"),
                rs.getDate("reservation_date").toLocalDate(),
                rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(),
                rs.getInt("attendee_count"),
                rs.getString("used_items"),
                rs.getString("needed_items"),
                rs.getString("purpose"),
                rs.getString("status_code"),
                rs.getString("manager_note"),
                toLocalDateTime(rs.getTimestamp("created_at")));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void upsertSeminar(
            String instCode,
            String seminarName,
            String roomName,
            Integer capacity,
            String useYn,
            String createdBy) {
        if (isMySql()) {
            jdbcTemplate.update("""
                    INSERT INTO mp_seminar_room (
                        inst_code, seminar_name, room_name, capacity, use_yn, created_by, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        room_name = ?,
                        capacity = ?,
                        use_yn = ?,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    instCode,
                    seminarName,
                    roomName,
                    capacity,
                    useYn,
                    createdBy,
                    roomName,
                    capacity,
                    useYn);
            return;
        }
        jdbcTemplate.update("""
                MERGE INTO mp_seminar_room (
                    inst_code, seminar_name, room_name, capacity, use_yn, created_by, created_at, updated_at
                ) KEY (inst_code, seminar_name)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                instCode,
                seminarName,
                roomName,
                capacity,
                useYn,
                createdBy);
    }

    private void updateSeminar(
            String instCode,
            Long seminarId,
            String seminarName,
            String roomName,
            Integer capacity,
            String useYn) {
        SeminarRoom existingSeminar = findSeminar(instCode, seminarId);
        if (existingSeminar == null) {
            throw new IllegalArgumentException("수정할 세미나를 찾을 수 없습니다.");
        }
        Integer duplicateCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_seminar_room
                WHERE inst_code = ?
                  AND LOWER(seminar_name) = LOWER(?)
                  AND id <> ?
                """, Integer.class,
                instCode,
                seminarName,
                seminarId);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("동일한 세미나명이 이미 등록되어 있습니다.");
        }
        int updatedCount = jdbcTemplate.update("""
                UPDATE mp_seminar_room
                SET seminar_name = ?,
                    room_name = ?,
                    capacity = ?,
                    use_yn = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE inst_code = ?
                  AND id = ?
                """,
                seminarName,
                roomName,
                capacity,
                useYn,
                instCode,
                seminarId);
        if (updatedCount <= 0) {
            throw new IllegalArgumentException("세미나 수정에 실패했습니다.");
        }
    }

    private void createTables() {
        if (isMySql()) {
            createMySqlTables();
            return;
        }
        createH2Tables();
    }

    private void createMySqlTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_room (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    seminar_name VARCHAR(120) NOT NULL,
                    room_name VARCHAR(120) NOT NULL,
                    capacity INT NOT NULL DEFAULT 1,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_mp_seminar_room (inst_code, seminar_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_manager (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    seminar_id BIGINT NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_mp_seminar_manager (inst_code, seminar_id, username)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_reservation (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    seminar_id BIGINT NOT NULL,
                    requester_username VARCHAR(100) NOT NULL,
                    requester_name VARCHAR(100) NOT NULL,
                    reservation_date DATE NOT NULL,
                    start_time TIME NOT NULL,
                    end_time TIME NOT NULL,
                    attendee_count INT NOT NULL DEFAULT 1,
                    used_items VARCHAR(500),
                    needed_items VARCHAR(500),
                    purpose VARCHAR(500),
                    status_code VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    manager_note VARCHAR(500),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_notification (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    reservation_id BIGINT NOT NULL,
                    inst_code VARCHAR(50) NOT NULL,
                    manager_username VARCHAR(100) NOT NULL,
                    message VARCHAR(500) NOT NULL,
                    read_yn CHAR(1) NOT NULL DEFAULT 'N',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void createH2Tables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_room (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    seminar_name VARCHAR(120) NOT NULL,
                    room_name VARCHAR(120) NOT NULL,
                    capacity INT NOT NULL DEFAULT 1,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (inst_code, seminar_name)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_manager (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    seminar_id BIGINT NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (inst_code, seminar_id, username)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_reservation (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    seminar_id BIGINT NOT NULL,
                    requester_username VARCHAR(100) NOT NULL,
                    requester_name VARCHAR(100) NOT NULL,
                    reservation_date DATE NOT NULL,
                    start_time TIME NOT NULL,
                    end_time TIME NOT NULL,
                    attendee_count INT NOT NULL DEFAULT 1,
                    used_items VARCHAR(500),
                    needed_items VARCHAR(500),
                    purpose VARCHAR(500),
                    status_code VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    manager_note VARCHAR(500),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_seminar_notification (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    reservation_id BIGINT NOT NULL,
                    inst_code VARCHAR(50) NOT NULL,
                    manager_username VARCHAR(100) NOT NULL,
                    message VARCHAR(500) NOT NULL,
                    read_yn CHAR(1) NOT NULL DEFAULT 'N',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private DatabaseDialect detectDatabaseDialect() {
        DatabaseDialect detected = jdbcTemplate.execute((ConnectionCallback<DatabaseDialect>) connection -> {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null && productName.toLowerCase(Locale.ROOT).contains("mysql")) {
                return DatabaseDialect.MYSQL;
            }
            return DatabaseDialect.H2;
        });
        return detected == null ? DatabaseDialect.H2 : detected;
    }

    private boolean isMySql() {
        return databaseDialect == DatabaseDialect.MYSQL;
    }

    private String normalizeInstCode(String instCode) {
        if (!StringUtils.hasText(instCode)) {
            return null;
        }
        String normalized = instCode.trim();
        if ("core".equalsIgnoreCase(normalized)) {
            return "core";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.trim();
    }

    private String normalizeYn(String useYn) {
        return "N".equalsIgnoreCase(useYn) ? "N" : USE_Y;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private enum DatabaseDialect {
        H2,
        MYSQL
    }
}
