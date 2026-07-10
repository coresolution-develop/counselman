package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.mediplat.model.FleetDriver;
import com.coresolution.mediplat.model.FleetTripLog;
import com.coresolution.mediplat.model.FleetVehicle;

class FleetTripServiceTest {

    private static final String PHOTO_START = "core/2026/07/start.jpg";
    private static final String PHOTO_END = "core/2026/07/end.jpg";

    @Test
    void depart_startsTripAndClaimsVehicle() {
        Fixture f = newFixture(1500);

        FleetTripLog trip = f.service.depart("core", f.vehicleId, f.driverId, "hong",
                "BUSINESS", "고객사 방문", 45010, "OCR", PHOTO_START);

        assertTrue(trip.isOngoing());
        assertEquals(45010, trip.getOdometerStart());
        assertEquals("BUSINESS", trip.getPurposeCode());

        FleetVehicle vehicle = f.service.findVehicle("core", f.vehicleId);
        assertEquals(FleetService.VEHICLE_STATUS_RUNNING, vehicle.getStatusCode());
        assertEquals(45010, vehicle.getCurrentOdometer());
    }

    @Test
    void depart_rejectsSecondDepartWhileRunning() {
        Fixture f = newFixture(1500);
        f.service.depart("core", f.vehicleId, f.driverId, "hong", "BUSINESS", null, 45010, "OCR", PHOTO_START);

        assertThrows(IllegalStateException.class, () -> f.service.depart(
                "core", f.vehicleId, f.driverId, "hong", "GENERAL", null, 45020, "MANUAL", PHOTO_START));
    }

    @Test
    void depart_rejectsOdometerRegression() {
        Fixture f = newFixture(1500);

        assertThrows(IllegalArgumentException.class, () -> f.service.depart(
                "core", f.vehicleId, f.driverId, "hong", "BUSINESS", null, 44000, "OCR", PHOTO_START));
    }

    @Test
    void depart_rejectsMissingPhoto() {
        Fixture f = newFixture(1500);

        assertThrows(IllegalArgumentException.class, () -> f.service.depart(
                "core", f.vehicleId, f.driverId, "hong", "BUSINESS", null, 45010, "OCR", null));
    }

    @Test
    void arrive_completesTripAndReturnsVehicle() {
        Fixture f = newFixture(1500);
        FleetTripLog started = f.service.depart("core", f.vehicleId, f.driverId, "hong",
                "BUSINESS", null, 45010, "OCR", PHOTO_START);

        FleetTripLog done = f.service.arrive("core", started.getId(), f.driverId, 45080, "OCR", PHOTO_END);

        assertTrue(done.isCompleted());
        assertEquals(70, done.getDistance());
        assertFalse(done.isOverLimit());

        FleetVehicle vehicle = f.service.findVehicle("core", f.vehicleId);
        assertEquals(FleetService.VEHICLE_STATUS_IDLE, vehicle.getStatusCode());
        assertEquals(45080, vehicle.getCurrentOdometer());
    }

    @Test
    void arrive_rejectsEndNotGreaterThanStart() {
        Fixture f = newFixture(1500);
        FleetTripLog started = f.service.depart("core", f.vehicleId, f.driverId, "hong",
                "BUSINESS", null, 45010, "OCR", PHOTO_START);

        assertThrows(IllegalArgumentException.class,
                () -> f.service.arrive("core", started.getId(), f.driverId, 45010, "OCR", PHOTO_END));
    }

    @Test
    void arrive_rejectsOtherDriver() {
        Fixture f = newFixture(1500);
        FleetTripLog started = f.service.depart("core", f.vehicleId, f.driverId, "hong",
                "BUSINESS", null, 45010, "OCR", PHOTO_START);
        FleetDriver other = f.service.registerDriver("core", "김기사", "kim", null, null, null);

        assertThrows(IllegalStateException.class,
                () -> f.service.arrive("core", started.getId(), other.getId(), 45080, "OCR", PHOTO_END));
    }

    @Test
    void arrive_flagsOverLimitButStillCompletes() {
        Fixture f = newFixture(50); // 상한 50km
        FleetTripLog started = f.service.depart("core", f.vehicleId, f.driverId, "hong",
                "BUSINESS", null, 45010, "OCR", PHOTO_START);

        FleetTripLog done = f.service.arrive("core", started.getId(), f.driverId, 45080, "OCR", PHOTO_END);

        assertTrue(done.isCompleted());
        assertTrue(done.isOverLimit(), "70km > 상한 50km 이면 경고 플래그");
    }

    private Fixture newFixture(int maxTripKm) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:fleet-trip-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        FleetService service = new FleetService(new JdbcTemplate(dataSource));
        service.initialize();
        ReflectionTestUtils.setField(service, "maxTripKm", maxTripKm);

        FleetVehicle vehicle = service.registerVehicle("core", "12가3456", "스타렉스 1호", null, "총무", 45000);
        FleetDriver driver = service.registerDriver("core", "홍길동", "hong", "E1001", "총무", null);
        return new Fixture(service, vehicle.getId(), driver.getId());
    }

    private record Fixture(FleetService service, Long vehicleId, Long driverId) {
    }
}
