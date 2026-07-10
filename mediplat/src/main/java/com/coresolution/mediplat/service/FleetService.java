package com.coresolution.mediplat.service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.mediplat.model.FleetDriver;
import com.coresolution.mediplat.model.FleetTripLog;
import com.coresolution.mediplat.model.FleetVehicle;

import jakarta.annotation.PostConstruct;

/**
 * 차량운행관리(fleet) 코어 서비스.
 *
 * <p>플랫폼 관례를 그대로 따른다: 순수 {@link JdbcTemplate} + 인라인 text-block SQL,
 * 불변 VO, {@code inst_code} 멀티테넌트 필터, {@code @PostConstruct}에서
 * {@code CREATE TABLE IF NOT EXISTS} 부트스트랩(MySQL/H2 분기). Flyway·JPA·MyBatis·Lombok 미사용.
 *
 * <p>P0 1단계 범위: 테이블 부트스트랩 + 차량 대장 + 운전자 로스터 기본 CRUD.
 * 출발/도착(depart/arrive, {@code @Transactional} + 조건부 UPDATE) · 사진 저장 · 기기 인증은
 * 후속 단계에서 추가한다({@code mp_fleet_trip_log} 테이블은 여기서 미리 생성해 둔다).
 */
@Service
public class FleetService {

    public static final String VEHICLE_STATUS_IDLE = "IDLE";
    public static final String VEHICLE_STATUS_RUNNING = "RUNNING";
    public static final String VEHICLE_STATUS_MAINTENANCE = "MAINTENANCE";
    public static final String VEHICLE_STATUS_DISABLED = "DISABLED";

    public static final String PURPOSE_BUSINESS = "BUSINESS";
    public static final String PURPOSE_COMMUTE = "COMMUTE";
    public static final String PURPOSE_GENERAL = "GENERAL";

    public static final String TRIP_STATUS_ONGOING = "ONGOING";
    public static final String TRIP_STATUS_COMPLETED = "COMPLETED";

    public static final String ODOMETER_SRC_MANUAL = "MANUAL";
    public static final String ODOMETER_SRC_OCR = "OCR";

    private static final String USE_Y = "Y";
    private static final HexFormat HEX = HexFormat.of();

    private static final String TRIP_SELECT = """
            SELECT id, inst_code, vehicle_id, driver_id, driver_username,
                   purpose_code, purpose_memo, depart_at, arrive_at,
                   odometer_start, odometer_end, distance,
                   odometer_start_src, odometer_end_src,
                   start_photo_path, end_photo_path, status_code, over_limit_yn, created_at
            FROM mp_fleet_trip_log
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private DatabaseDialect databaseDialect = DatabaseDialect.H2;

    /** 1회 주행 상한(km). 초과 시 차단하지 않고 경고 플래그만 세운다. */
    @Value("${fleet.trip.max-km:1500}")
    private int maxTripKm;

    public FleetService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        databaseDialect = detectDatabaseDialect();
        createTables();
    }

    // ---------------------------------------------------------------------
    // 차량 대장
    // ---------------------------------------------------------------------

    public List<FleetVehicle> listVehicles(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, inst_code, plate_no, name, model_name, department,
                       status_code, current_odometer, qr_token, use_yn, created_at, updated_at
                FROM mp_fleet_vehicle
                WHERE inst_code = ?
                ORDER BY use_yn DESC, plate_no ASC, id ASC
                """, (rs, rowNum) -> mapVehicle(rs),
                normalizedInstCode);
    }

    public FleetVehicle findVehicle(String instCode, Long vehicleId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || vehicleId == null) {
            return null;
        }
        List<FleetVehicle> result = jdbcTemplate.query("""
                SELECT id, inst_code, plate_no, name, model_name, department,
                       status_code, current_odometer, qr_token, use_yn, created_at, updated_at
                FROM mp_fleet_vehicle
                WHERE inst_code = ?
                  AND id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapVehicle(rs),
                normalizedInstCode,
                vehicleId);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * QR 토큰으로 차량을 해석한다. 토큰 자체가 전역 유니크 비밀값이므로 inst_code로 스코프하지 않는다
     * (스캔하는 운전자는 inst_code를 모른다). 반환된 차량의 {@code instCode}로 이후 흐름을 태운다.
     */
    public FleetVehicle findVehicleByQrToken(String qrToken) {
        String normalizedToken = trimToNull(qrToken);
        if (normalizedToken == null) {
            return null;
        }
        List<FleetVehicle> result = jdbcTemplate.query("""
                SELECT id, inst_code, plate_no, name, model_name, department,
                       status_code, current_odometer, qr_token, use_yn, created_at, updated_at
                FROM mp_fleet_vehicle
                WHERE qr_token = ?
                LIMIT 1
                """, (rs, rowNum) -> mapVehicle(rs),
                normalizedToken);
        return result.isEmpty() ? null : result.get(0);
    }

    /** 차량 신규 등록(qr_token 자동 발급, 상태 IDLE). 등록된 차량을 반환한다. */
    public FleetVehicle registerVehicle(
            String instCode,
            String plateNo,
            String name,
            String modelName,
            String department,
            Integer initialOdometer) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedPlateNo = trimToNull(plateNo);
        if (!StringUtils.hasText(normalizedInstCode) || normalizedPlateNo == null) {
            throw new IllegalArgumentException("기관 코드와 차량 번호판은 필수입니다.");
        }
        Integer duplicateCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM mp_fleet_vehicle
                WHERE inst_code = ?
                  AND plate_no = ?
                """, Integer.class,
                normalizedInstCode,
                normalizedPlateNo);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("이미 등록된 차량 번호판입니다.");
        }
        int normalizedOdometer = initialOdometer == null || initialOdometer < 0 ? 0 : initialOdometer;
        String qrToken = generateUniqueQrToken();
        jdbcTemplate.update("""
                INSERT INTO mp_fleet_vehicle (
                    inst_code, plate_no, name, model_name, department,
                    status_code, current_odometer, qr_token, use_yn, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                normalizedInstCode,
                normalizedPlateNo,
                trimToNull(name),
                trimToNull(modelName),
                trimToNull(department),
                VEHICLE_STATUS_IDLE,
                normalizedOdometer,
                qrToken);
        return findVehicleByQrToken(qrToken);
    }

    /** QR 재발급(qr_token 회전). 인쇄물 분실·유출 대비. */
    public FleetVehicle regenerateQrToken(String instCode, Long vehicleId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || vehicleId == null) {
            throw new IllegalArgumentException("차량 정보를 확인해 주세요.");
        }
        FleetVehicle vehicle = findVehicle(normalizedInstCode, vehicleId);
        if (vehicle == null) {
            throw new IllegalArgumentException("등록된 차량을 찾을 수 없습니다.");
        }
        String qrToken = generateUniqueQrToken();
        int updated = jdbcTemplate.update("""
                UPDATE mp_fleet_vehicle
                SET qr_token = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE inst_code = ?
                  AND id = ?
                """,
                qrToken,
                normalizedInstCode,
                vehicleId);
        if (updated <= 0) {
            throw new IllegalArgumentException("QR 재발급에 실패했습니다.");
        }
        return findVehicle(normalizedInstCode, vehicleId);
    }

    // ---------------------------------------------------------------------
    // 운전자 로스터
    // ---------------------------------------------------------------------

    public List<FleetDriver> listDrivers(String instCode) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, inst_code, name, username, employee_number, department,
                       phone, external_user_ref, use_yn, created_at, updated_at
                FROM mp_fleet_driver
                WHERE inst_code = ?
                ORDER BY use_yn DESC, name ASC, id ASC
                """, (rs, rowNum) -> mapDriver(rs),
                normalizedInstCode);
    }

    /**
     * 운전자를 id 단독으로 조회한다(inst_code 미지정). 기기 토큰 검증은 driverId만 알고 있어
     * inst_code 스코프 없이 신원을 복원해야 하므로 필요하다. driver_id는 전역 PK라 안전하다.
     */
    public FleetDriver findDriverById(Long driverId) {
        if (driverId == null) {
            return null;
        }
        List<FleetDriver> result = jdbcTemplate.query("""
                SELECT id, inst_code, name, username, employee_number, department,
                       phone, external_user_ref, use_yn, created_at, updated_at
                FROM mp_fleet_driver
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapDriver(rs),
                driverId);
        return result.isEmpty() ? null : result.get(0);
    }

    public FleetDriver findDriver(String instCode, Long driverId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || driverId == null) {
            return null;
        }
        List<FleetDriver> result = jdbcTemplate.query("""
                SELECT id, inst_code, name, username, employee_number, department,
                       phone, external_user_ref, use_yn, created_at, updated_at
                FROM mp_fleet_driver
                WHERE inst_code = ?
                  AND id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapDriver(rs),
                normalizedInstCode,
                driverId);
        return result.isEmpty() ? null : result.get(0);
    }

    /** 운전자 신규 등록. {@code username}은 (inst_code, username) 자연키로 플랫폼 계정과 느슨하게 연결(선택). */
    public FleetDriver registerDriver(
            String instCode,
            String name,
            String username,
            String employeeNumber,
            String department,
            String phone) {
        String normalizedInstCode = normalizeInstCode(instCode);
        String normalizedName = trimToNull(name);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedInstCode) || normalizedName == null) {
            throw new IllegalArgumentException("기관 코드와 운전자 이름은 필수입니다.");
        }
        if (normalizedUsername != null) {
            Integer duplicateCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM mp_fleet_driver
                    WHERE inst_code = ?
                      AND LOWER(username) = LOWER(?)
                    """, Integer.class,
                    normalizedInstCode,
                    normalizedUsername);
            if (duplicateCount != null && duplicateCount > 0) {
                throw new IllegalArgumentException("이미 등록된 계정의 운전자입니다.");
            }
        }
        jdbcTemplate.update("""
                INSERT INTO mp_fleet_driver (
                    inst_code, name, username, employee_number, department, phone,
                    external_user_ref, use_yn, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                normalizedInstCode,
                normalizedName,
                normalizedUsername,
                trimToNull(employeeNumber),
                trimToNull(department),
                trimToNull(phone));
        return findLatestDriver(normalizedInstCode, normalizedName, normalizedUsername);
    }

    private FleetDriver findLatestDriver(String instCode, String name, String username) {
        List<FleetDriver> result = jdbcTemplate.query("""
                SELECT id, inst_code, name, username, employee_number, department,
                       phone, external_user_ref, use_yn, created_at, updated_at
                FROM mp_fleet_driver
                WHERE inst_code = ?
                  AND name = ?
                  AND ((username IS NULL AND ? IS NULL) OR username = ?)
                ORDER BY id DESC
                LIMIT 1
                """, (rs, rowNum) -> mapDriver(rs),
                instCode,
                name,
                username,
                username);
        return result.isEmpty() ? null : result.get(0);
    }

    // ---------------------------------------------------------------------
    // 운행 기록 (출발/도착)
    // ---------------------------------------------------------------------

    public FleetTripLog findTrip(String instCode, Long tripId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || tripId == null) {
            return null;
        }
        List<FleetTripLog> result = jdbcTemplate.query(TRIP_SELECT + """
                WHERE inst_code = ?
                  AND id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapTrip(rs), normalizedInstCode, tripId);
        return result.isEmpty() ? null : result.get(0);
    }

    /** 차량의 진행 중(ONGOING) 운행. 없으면 null. by-token 상태 분기·중복 출발 방지에 쓴다. */
    public FleetTripLog findOngoingTripByVehicle(String instCode, Long vehicleId) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || vehicleId == null) {
            return null;
        }
        List<FleetTripLog> result = jdbcTemplate.query(TRIP_SELECT + """
                WHERE inst_code = ?
                  AND vehicle_id = ?
                  AND status_code = 'ONGOING'
                ORDER BY id DESC
                LIMIT 1
                """, (rs, rowNum) -> mapTrip(rs), normalizedInstCode, vehicleId);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * 출발 기록. 사진은 <b>이 메서드 밖(트랜잭션 밖)에서 먼저 저장</b>하고 경로만 넘긴다(되짚음 ①).
     * 차량 점유는 {@code status='IDLE'} 조건부 UPDATE로 원자화하며, 0행이면 이미 사용 중으로 명확히 실패한다.
     */
    @Transactional
    public FleetTripLog depart(
            String instCode,
            Long vehicleId,
            Long driverId,
            String driverUsername,
            String purposeCode,
            String purposeMemo,
            Integer odometerStart,
            String odometerStartSrc,
            String startPhotoPath) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || vehicleId == null || driverId == null) {
            throw new IllegalArgumentException("운행 정보를 확인해 주세요.");
        }
        if (!StringUtils.hasText(startPhotoPath)) {
            throw new IllegalArgumentException("출발 계기판 사진은 필수입니다.");
        }
        if (odometerStart == null || odometerStart < 0) {
            throw new IllegalArgumentException("출발 계기판 값을 확인해 주세요.");
        }
        String normalizedPurpose = normalizePurpose(purposeCode);
        String normalizedSrc = normalizeOdometerSrc(odometerStartSrc);

        FleetVehicle vehicle = findVehicle(normalizedInstCode, vehicleId);
        if (vehicle == null || !vehicle.isEnabled()) {
            throw new IllegalArgumentException("운행 가능한 차량을 찾을 수 없습니다.");
        }
        FleetDriver driver = findDriverById(driverId);
        if (driver == null || !driver.isEnabled()) {
            throw new IllegalArgumentException("등록된 운전자를 찾을 수 없습니다.");
        }
        // 서버 재검증: 계기판 단조 증가 (OCR 프리필 불신, 되짚음 ②)
        if (odometerStart < vehicle.getCurrentOdometer()) {
            throw new IllegalArgumentException("출발 계기판 값이 기존 기록보다 작습니다.");
        }

        // 원자적 점유: IDLE일 때만 RUNNING으로. 0행이면 이미 사용 중(동시성).
        int claimed = jdbcTemplate.update("""
                UPDATE mp_fleet_vehicle
                   SET status_code = 'RUNNING',
                       current_odometer = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE inst_code = ?
                   AND id = ?
                   AND status_code = 'IDLE'
                """, odometerStart, normalizedInstCode, vehicleId);
        if (claimed == 0) {
            throw new IllegalStateException("차량이 이미 사용 중입니다.");
        }

        jdbcTemplate.update("""
                INSERT INTO mp_fleet_trip_log (
                    inst_code, vehicle_id, driver_id, driver_username,
                    purpose_code, purpose_memo, depart_at,
                    odometer_start, odometer_start_src, start_photo_path,
                    status_code, over_limit_yn, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, 'ONGOING', 'N', CURRENT_TIMESTAMP)
                """,
                normalizedInstCode, vehicleId, driverId, trimToNull(driverUsername),
                normalizedPurpose, trimToNull(purposeMemo),
                odometerStart, normalizedSrc, startPhotoPath);

        return findOngoingTripByVehicle(normalizedInstCode, vehicleId);
    }

    /**
     * 도착 기록. 종료 계기판 사진은 트랜잭션 밖에서 먼저 저장하고 경로만 넘긴다(되짚음 ①).
     * 종료는 {@code status='ONGOING'} 조건부 UPDATE로 원자화하고, 차량을 IDLE로 반납한다.
     */
    @Transactional
    public FleetTripLog arrive(
            String instCode,
            Long tripId,
            Long driverId,
            Integer odometerEnd,
            String odometerEndSrc,
            String endPhotoPath) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode) || tripId == null || driverId == null) {
            throw new IllegalArgumentException("운행 정보를 확인해 주세요.");
        }
        if (!StringUtils.hasText(endPhotoPath)) {
            throw new IllegalArgumentException("도착 계기판 사진은 필수입니다.");
        }
        if (odometerEnd == null || odometerEnd < 0) {
            throw new IllegalArgumentException("도착 계기판 값을 확인해 주세요.");
        }
        String normalizedSrc = normalizeOdometerSrc(odometerEndSrc);

        FleetTripLog trip = findTrip(normalizedInstCode, tripId);
        if (trip == null) {
            throw new IllegalArgumentException("운행 기록을 찾을 수 없습니다.");
        }
        if (!trip.isOngoing()) {
            throw new IllegalStateException("이미 종료된 운행입니다.");
        }
        if (!driverId.equals(trip.getDriverId())) {
            throw new IllegalStateException("본인이 시작한 운행만 종료할 수 있습니다.");
        }
        // 서버 재검증: 종료 > 시작 (OCR 프리필 불신, 되짚음 ②)
        if (odometerEnd <= trip.getOdometerStart()) {
            throw new IllegalArgumentException("도착 계기판은 출발 계기판보다 커야 합니다.");
        }
        int distance = odometerEnd - trip.getOdometerStart();
        String overLimitYn = distance > maxTripKm ? "Y" : "N";

        int completed = jdbcTemplate.update("""
                UPDATE mp_fleet_trip_log
                   SET arrive_at = CURRENT_TIMESTAMP,
                       odometer_end = ?,
                       distance = ?,
                       odometer_end_src = ?,
                       end_photo_path = ?,
                       status_code = 'COMPLETED',
                       over_limit_yn = ?
                 WHERE inst_code = ?
                   AND id = ?
                   AND status_code = 'ONGOING'
                """,
                odometerEnd, distance, normalizedSrc, endPhotoPath, overLimitYn,
                normalizedInstCode, tripId);
        if (completed == 0) {
            throw new IllegalStateException("운행 종료 처리에 실패했습니다.");
        }

        // 차량 반납: RUNNING → IDLE, 최신 계기판 갱신
        jdbcTemplate.update("""
                UPDATE mp_fleet_vehicle
                   SET status_code = 'IDLE',
                       current_odometer = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE inst_code = ?
                   AND id = ?
                """, odometerEnd, normalizedInstCode, trip.getVehicleId());

        return findTrip(normalizedInstCode, tripId);
    }

    /**
     * 관리자 운행 조회. 필터(차량·목적·기간)는 모두 선택이며, null이면 무시한다.
     * inst_code로 스코프되므로 관리자는 자기 기관(core 관리자는 core 전체)의 운행만 본다.
     */
    public List<FleetTripLog> listTrips(
            String instCode,
            Long vehicleId,
            String purposeCode,
            LocalDate fromDate,
            LocalDate toDate) {
        String normalizedInstCode = normalizeInstCode(instCode);
        if (!StringUtils.hasText(normalizedInstCode)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(TRIP_SELECT).append(" WHERE inst_code = ?");
        List<Object> params = new ArrayList<>();
        params.add(normalizedInstCode);
        if (vehicleId != null) {
            sql.append(" AND vehicle_id = ?");
            params.add(vehicleId);
        }
        if (isValidPurpose(purposeCode)) {
            sql.append(" AND purpose_code = ?");
            params.add(purposeCode.trim().toUpperCase(Locale.ROOT));
        }
        if (fromDate != null) {
            sql.append(" AND depart_at >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append(" AND depart_at < ?");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY depart_at DESC, id DESC LIMIT 500");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTrip(rs), params.toArray());
    }

    private boolean isValidPurpose(String purposeCode) {
        if (!StringUtils.hasText(purposeCode)) {
            return false;
        }
        String upper = purposeCode.trim().toUpperCase(Locale.ROOT);
        return PURPOSE_BUSINESS.equals(upper) || PURPOSE_COMMUTE.equals(upper) || PURPOSE_GENERAL.equals(upper);
    }

    // ---------------------------------------------------------------------
    // RowMappers
    // ---------------------------------------------------------------------

    private FleetVehicle mapVehicle(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FleetVehicle(
                rs.getLong("id"),
                rs.getString("inst_code"),
                rs.getString("plate_no"),
                rs.getString("name"),
                rs.getString("model_name"),
                rs.getString("department"),
                rs.getString("status_code"),
                rs.getInt("current_odometer"),
                rs.getString("qr_token"),
                rs.getString("use_yn"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")));
    }

    private FleetDriver mapDriver(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FleetDriver(
                rs.getLong("id"),
                rs.getString("inst_code"),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("employee_number"),
                rs.getString("department"),
                rs.getString("phone"),
                rs.getString("external_user_ref"),
                rs.getString("use_yn"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")));
    }

    private FleetTripLog mapTrip(java.sql.ResultSet rs) throws java.sql.SQLException {
        Integer odometerEnd = rs.getObject("odometer_end") == null ? null : rs.getInt("odometer_end");
        Integer distance = rs.getObject("distance") == null ? null : rs.getInt("distance");
        return new FleetTripLog(
                rs.getLong("id"),
                rs.getString("inst_code"),
                rs.getLong("vehicle_id"),
                rs.getLong("driver_id"),
                rs.getString("driver_username"),
                rs.getString("purpose_code"),
                rs.getString("purpose_memo"),
                toLocalDateTime(rs.getTimestamp("depart_at")),
                toLocalDateTime(rs.getTimestamp("arrive_at")),
                rs.getInt("odometer_start"),
                odometerEnd,
                distance,
                rs.getString("odometer_start_src"),
                rs.getString("odometer_end_src"),
                rs.getString("start_photo_path"),
                rs.getString("end_photo_path"),
                rs.getString("status_code"),
                rs.getString("over_limit_yn"),
                toLocalDateTime(rs.getTimestamp("created_at")));
    }

    // ---------------------------------------------------------------------
    // QR 토큰
    // ---------------------------------------------------------------------

    private String generateUniqueQrToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = generateToken();
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mp_fleet_vehicle WHERE qr_token = ?",
                    Integer.class,
                    token);
            if (count == null || count == 0) {
                return token;
            }
        }
        throw new IllegalStateException("QR 토큰 생성에 실패했습니다.");
    }

    private String generateToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    // ---------------------------------------------------------------------
    // 테이블 부트스트랩
    // ---------------------------------------------------------------------

    private void createTables() {
        if (isMySql()) {
            createMySqlTables();
            return;
        }
        createH2Tables();
    }

    private void createMySqlTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_vehicle (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    plate_no VARCHAR(20) NOT NULL,
                    name VARCHAR(120),
                    model_name VARCHAR(120),
                    department VARCHAR(120),
                    status_code VARCHAR(20) NOT NULL DEFAULT 'IDLE',
                    current_odometer INT NOT NULL DEFAULT 0,
                    qr_token VARCHAR(64) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_mp_fleet_vehicle_plate (inst_code, plate_no),
                    UNIQUE KEY uq_mp_fleet_vehicle_qr (qr_token)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_driver (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    username VARCHAR(100),
                    employee_number VARCHAR(50),
                    department VARCHAR(120),
                    phone VARCHAR(30),
                    external_user_ref VARCHAR(64),
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_mp_fleet_driver_username (inst_code, username),
                    KEY idx_mp_fleet_driver_inst (inst_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_trip_log (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    vehicle_id BIGINT NOT NULL,
                    driver_id BIGINT NOT NULL,
                    driver_username VARCHAR(100),
                    purpose_code VARCHAR(20) NOT NULL,
                    purpose_memo VARCHAR(500),
                    depart_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    arrive_at TIMESTAMP NULL DEFAULT NULL,
                    odometer_start INT NOT NULL,
                    odometer_end INT,
                    distance INT,
                    odometer_start_src VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
                    odometer_end_src VARCHAR(10),
                    start_photo_path VARCHAR(300) NOT NULL,
                    end_photo_path VARCHAR(300),
                    status_code VARCHAR(20) NOT NULL DEFAULT 'ONGOING',
                    over_limit_yn CHAR(1) NOT NULL DEFAULT 'N',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_mp_fleet_trip_vehicle (inst_code, vehicle_id, depart_at),
                    KEY idx_mp_fleet_trip_driver (inst_code, driver_id),
                    KEY idx_mp_fleet_trip_status (inst_code, vehicle_id, status_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_device_token (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    driver_id BIGINT NOT NULL,
                    selector VARCHAR(24) NOT NULL,
                    token_hash VARCHAR(100) NOT NULL,
                    expires_at DATETIME NOT NULL,
                    last_used_at DATETIME,
                    user_agent VARCHAR(255),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_mp_fleet_device_selector (selector),
                    KEY idx_mp_fleet_device_driver (driver_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void createH2Tables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_vehicle (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    plate_no VARCHAR(20) NOT NULL,
                    name VARCHAR(120),
                    model_name VARCHAR(120),
                    department VARCHAR(120),
                    status_code VARCHAR(20) NOT NULL DEFAULT 'IDLE',
                    current_odometer INT NOT NULL DEFAULT 0,
                    qr_token VARCHAR(64) NOT NULL,
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (inst_code, plate_no),
                    UNIQUE (qr_token)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_driver (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    username VARCHAR(100),
                    employee_number VARCHAR(50),
                    department VARCHAR(120),
                    phone VARCHAR(30),
                    external_user_ref VARCHAR(64),
                    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (inst_code, username)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_trip_log (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    inst_code VARCHAR(50) NOT NULL,
                    vehicle_id BIGINT NOT NULL,
                    driver_id BIGINT NOT NULL,
                    driver_username VARCHAR(100),
                    purpose_code VARCHAR(20) NOT NULL,
                    purpose_memo VARCHAR(500),
                    depart_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    arrive_at TIMESTAMP,
                    odometer_start INT NOT NULL,
                    odometer_end INT,
                    distance INT,
                    odometer_start_src VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
                    odometer_end_src VARCHAR(10),
                    start_photo_path VARCHAR(300) NOT NULL,
                    end_photo_path VARCHAR(300),
                    status_code VARCHAR(20) NOT NULL DEFAULT 'ONGOING',
                    over_limit_yn CHAR(1) NOT NULL DEFAULT 'N',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mp_fleet_device_token (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    driver_id BIGINT NOT NULL,
                    selector VARCHAR(24) NOT NULL,
                    token_hash VARCHAR(100) NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    last_used_at TIMESTAMP,
                    user_agent VARCHAR(255),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (selector)
                )
                """);
    }

    // ---------------------------------------------------------------------
    // 공통 헬퍼 (SeminarRoomService 관례 준수)
    // ---------------------------------------------------------------------

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

    private String normalizePurpose(String purposeCode) {
        if (PURPOSE_BUSINESS.equalsIgnoreCase(purposeCode)) {
            return PURPOSE_BUSINESS;
        }
        if (PURPOSE_COMMUTE.equalsIgnoreCase(purposeCode)) {
            return PURPOSE_COMMUTE;
        }
        if (PURPOSE_GENERAL.equalsIgnoreCase(purposeCode)) {
            return PURPOSE_GENERAL;
        }
        throw new IllegalArgumentException("운행 목적을 선택해 주세요.");
    }

    private String normalizeOdometerSrc(String src) {
        return ODOMETER_SRC_OCR.equalsIgnoreCase(src) ? ODOMETER_SRC_OCR : ODOMETER_SRC_MANUAL;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private enum DatabaseDialect {
        H2,
        MYSQL
    }
}
