package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.coresolution.mediplat.model.FleetDriver;

class FleetDeviceTokenServiceTest {

    private static final String UA = "JUnit/1.0";

    @Test
    void issueThenValidateAndRotate_succeedsAndRotatesValidator() {
        Fixture fixture = newFixture();
        long driverId = fixture.driver.getId();

        String cookie1 = fixture.tokenService.issue(driverId, UA);
        FleetDeviceTokenService.Result r1 = fixture.tokenService.validateAndRotate(cookie1, UA);

        assertTrue(r1.valid());
        assertEquals(driverId, r1.driverId());
        assertNotEquals(cookie1, r1.newCookieValue(), "validator가 회전되어야 한다");

        // 회전된 새 쿠키는 계속 유효
        FleetDeviceTokenService.Result r2 = fixture.tokenService.validateAndRotate(r1.newCookieValue(), UA);
        assertTrue(r2.valid());
        assertEquals(driverId, r2.driverId());
    }

    @Test
    void oldValidator_afterRotation_isRejected() {
        Fixture fixture = newFixture();
        String cookie1 = fixture.tokenService.issue(fixture.driver.getId(), UA);

        fixture.tokenService.validateAndRotate(cookie1, UA); // 회전 발생

        FleetDeviceTokenService.Result reused = fixture.tokenService.validateAndRotate(cookie1, UA);
        assertFalse(reused.valid(), "회전 전 validator는 무효여야 한다");
    }

    @Test
    void tamperedValidator_isRejected() {
        Fixture fixture = newFixture();
        String cookie = fixture.tokenService.issue(fixture.driver.getId(), UA);
        String selector = cookie.substring(0, cookie.indexOf(':'));

        FleetDeviceTokenService.Result result =
                fixture.tokenService.validateAndRotate(selector + ":forged-validator", UA);
        assertFalse(result.valid());
    }

    @Test
    void expiredToken_isRejected() {
        Fixture fixture = newFixture();
        String cookie = fixture.tokenService.issue(fixture.driver.getId(), UA);
        String selector = cookie.substring(0, cookie.indexOf(':'));
        fixture.jdbcTemplate.update(
                "UPDATE mp_fleet_device_token SET expires_at = ? WHERE selector = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)), selector);

        FleetDeviceTokenService.Result result = fixture.tokenService.validateAndRotate(cookie, UA);
        assertFalse(result.valid());
    }

    @Test
    void inactiveDriver_isRejected() {
        Fixture fixture = newFixture();
        String cookie = fixture.tokenService.issue(fixture.driver.getId(), UA);
        fixture.jdbcTemplate.update(
                "UPDATE mp_fleet_driver SET use_yn = 'N' WHERE id = ?", fixture.driver.getId());

        FleetDeviceTokenService.Result result = fixture.tokenService.validateAndRotate(cookie, UA);
        assertFalse(result.valid());
    }

    @Test
    void deleteByCookie_revokesToken() {
        Fixture fixture = newFixture();
        String cookie = fixture.tokenService.issue(fixture.driver.getId(), UA);

        fixture.tokenService.deleteByCookie(cookie);

        assertFalse(fixture.tokenService.validateAndRotate(cookie, UA).valid());
    }

    private Fixture newFixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:fleet-device-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        FleetService fleetService = new FleetService(jdbcTemplate);
        fleetService.initialize();
        FleetDriver driver = fleetService.registerDriver("core", "홍길동", "hong", "E1001", "총무", null);

        FleetDeviceTokenService tokenService = new FleetDeviceTokenService(jdbcTemplate);
        ReflectionTestUtils.setField(tokenService, "rememberDays", 90);
        ReflectionTestUtils.setField(tokenService, "cookieSecure", false);

        return new Fixture(jdbcTemplate, fleetService, driver, tokenService);
    }

    private record Fixture(
            JdbcTemplate jdbcTemplate,
            FleetService fleetService,
            FleetDriver driver,
            FleetDeviceTokenService tokenService) {
    }
}
