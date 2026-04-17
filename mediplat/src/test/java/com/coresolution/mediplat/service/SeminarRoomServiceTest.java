package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.coresolution.mediplat.model.SeminarReservation;
import com.coresolution.mediplat.model.SeminarRoom;

class SeminarRoomServiceTest {

    @Test
    void createReservation_createsNotificationAndBlocksOverlappingTime() {
        SeminarRoomService seminarRoomService = newInitializedService();

        seminarRoomService.saveSeminar("FALH", "월간 세미나", "A 세미나실", 20, "Y", "admin");
        List<SeminarRoom> seminars = seminarRoomService.listSeminars("FALH");
        assertEquals(1, seminars.size());
        Long seminarId = seminars.get(0).getId();
        seminarRoomService.saveSeminarManagers("FALH", seminarId, List.of("manager1"));

        SeminarReservation reservation = seminarRoomService.createReservation(
                "FALH",
                seminarId,
                "user1",
                "사용자1",
                LocalDate.of(2026, 4, 17),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                10,
                "프로젝터",
                "생수 10병",
                "교육");

        assertNotNull(reservation);
        assertEquals("PENDING", reservation.getStatusCode());
        assertEquals(1, seminarRoomService.countUnreadNotifications("FALH", "manager1"));

        assertThrows(IllegalArgumentException.class, () -> seminarRoomService.createReservation(
                "FALH",
                seminarId,
                "user2",
                "사용자2",
                LocalDate.of(2026, 4, 17),
                LocalTime.of(10, 30),
                LocalTime.of(11, 30),
                8,
                "",
                "",
                "중복 테스트"));
    }

    @Test
    void saveSeminar_updatesExistingSeminar_whenSeminarIdProvided() {
        SeminarRoomService seminarRoomService = newInitializedService();

        seminarRoomService.saveSeminar("FALH", "월간 세미나", "A 세미나실", 20, "Y", "admin");
        SeminarRoom createdSeminar = seminarRoomService.listSeminars("FALH").get(0);

        seminarRoomService.saveSeminar("FALH", createdSeminar.getId(), "정기 교육 세미나", "B 세미나실", 40, "N", "admin");

        List<SeminarRoom> seminars = seminarRoomService.listSeminars("FALH");
        assertEquals(1, seminars.size());
        SeminarRoom updatedSeminar = seminars.get(0);
        assertEquals(createdSeminar.getId(), updatedSeminar.getId());
        assertEquals("정기 교육 세미나", updatedSeminar.getSeminarName());
        assertEquals("B 세미나실", updatedSeminar.getRoomName());
        assertEquals(40, updatedSeminar.getCapacity());
        assertEquals("N", updatedSeminar.getUseYn());
    }

    private SeminarRoomService newInitializedService() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:seminar-room-service-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        SeminarRoomService service = new SeminarRoomService(new JdbcTemplate(dataSource));
        service.initialize();
        return service;
    }
}
