package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.coresolution.mediplat.model.FleetDriver;
import com.coresolution.mediplat.model.FleetVehicle;

class FleetServiceTest {

    @Test
    void registerVehicle_persistsAndIssuesQrToken() {
        FleetService fleetService = newInitializedService();

        FleetVehicle vehicle = fleetService.registerVehicle(
                "core", "12가3456", "스타렉스 1호", "그랜드 스타렉스", "총무", 45000);

        assertNotNull(vehicle);
        assertNotNull(vehicle.getId());
        assertEquals("core", vehicle.getInstCode());
        assertEquals("12가3456", vehicle.getPlateNo());
        assertEquals(45000, vehicle.getCurrentOdometer());
        assertEquals(FleetService.VEHICLE_STATUS_IDLE, vehicle.getStatusCode());
        assertTrue(vehicle.isAvailable());
        assertNotNull(vehicle.getQrToken());

        List<FleetVehicle> vehicles = fleetService.listVehicles("core");
        assertEquals(1, vehicles.size());
        assertEquals(vehicle.getId(), vehicles.get(0).getId());

        FleetVehicle byToken = fleetService.findVehicleByQrToken(vehicle.getQrToken());
        assertNotNull(byToken);
        assertEquals(vehicle.getId(), byToken.getId());
    }

    @Test
    void registerVehicle_rejectsDuplicatePlateWithinInstitution() {
        FleetService fleetService = newInitializedService();

        fleetService.registerVehicle("core", "34나5678", "카니발 2호", null, "영업", 30250);

        assertThrows(IllegalArgumentException.class, () -> fleetService.registerVehicle(
                "core", "34나5678", "카니발 3호", null, "영업", 0));
    }

    @Test
    void regenerateQrToken_rotatesToken() {
        FleetService fleetService = newInitializedService();

        FleetVehicle vehicle = fleetService.registerVehicle("core", "56다7890", null, null, null, 0);
        String originalToken = vehicle.getQrToken();

        FleetVehicle rotated = fleetService.regenerateQrToken("core", vehicle.getId());

        assertNotNull(rotated);
        assertEquals(vehicle.getId(), rotated.getId());
        assertNotEquals(originalToken, rotated.getQrToken());
        assertNotNull(fleetService.findVehicleByQrToken(rotated.getQrToken()));
    }

    @Test
    void registerDriver_persistsRosterEntryWithNaturalKey() {
        FleetService fleetService = newInitializedService();

        FleetDriver driver = fleetService.registerDriver(
                "core", "홍길동", "hong", "E1001", "총무", "010-1234-5678");

        assertNotNull(driver);
        assertNotNull(driver.getId());
        assertEquals("홍길동", driver.getName());
        assertEquals("hong", driver.getUsername());
        assertTrue(driver.isEnabled());

        assertEquals(1, fleetService.listDrivers("core").size());
    }

    @Test
    void registerDriver_rejectsDuplicateUsername() {
        FleetService fleetService = newInitializedService();

        fleetService.registerDriver("core", "홍길동", "hong", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> fleetService.registerDriver(
                "core", "다른사람", "hong", null, null, null));
    }

    @Test
    void registerDriver_allowsMultipleManualEntriesWithoutUsername() {
        FleetService fleetService = newInitializedService();

        fleetService.registerDriver("core", "김기사", null, null, null, null);
        fleetService.registerDriver("core", "이기사", null, null, null, null);

        assertEquals(2, fleetService.listDrivers("core").size());
    }

    @Test
    void listVehicles_isScopedByInstitution() {
        FleetService fleetService = newInitializedService();

        fleetService.registerVehicle("core", "11가1111", null, null, null, 0);
        fleetService.registerVehicle("FALH", "22나2222", null, null, null, 0);

        assertEquals(1, fleetService.listVehicles("core").size());
        assertEquals(1, fleetService.listVehicles("FALH").size());
    }

    private FleetService newInitializedService() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:fleet-service-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        FleetService service = new FleetService(new JdbcTemplate(dataSource));
        service.initialize();
        return service;
    }
}
