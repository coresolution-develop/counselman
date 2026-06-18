package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.ScheduleRecurrenceRequest;
import com.coresolution.cancertreatment.model.TreatmentRoom;
import com.coresolution.cancertreatment.model.TreatmentRoomRequest;
import com.coresolution.cancertreatment.model.TreatmentSchedule;
import com.coresolution.cancertreatment.model.TreatmentSeat;
import com.coresolution.cancertreatment.model.TreatmentSeatRequest;

@SpringBootTest
class TreatmentScheduleQueryTests {

    @Autowired private TreatmentRoomService treatmentRoomService;
    @Autowired private TreatmentSeatService treatmentSeatService;
    @Autowired private ScheduleRecurrenceService recurrenceService;
    @Autowired private TreatmentScheduleService scheduleService;

    private TreatmentRoom newRoom(String name) {
        TreatmentRoomRequest r = new TreatmentRoomRequest();
        r.setRoomName(name);
        return treatmentRoomService.createRoom("q", r);
    }

    private TreatmentSeat newSeat(Long roomId, String code) {
        TreatmentSeatRequest s = new TreatmentSeatRequest();
        s.setTreatmentRoomId(roomId);
        s.setSeatCode(code);
        s.setSeatName(code + "자리");
        return treatmentSeatService.createSeat("q", s);
    }

    private void register(Long roomId, Long seatId, String patient, String date) {
        ScheduleRecurrenceRequest req = new ScheduleRecurrenceRequest();
        req.setPatientName(patient);
        req.setTreatmentName("고주파");
        req.setTreatmentRoomId(roomId);
        req.setSeatId(seatId);
        req.setStartDate(date);
        req.setStartTime("10:00");
        req.setRepeat(false);
        recurrenceService.createSchedules("q", "tester", req);
    }

    @Test
    void filtersByRoomAndJoinsSeatName() {
        TreatmentRoom roomA = newRoom("A실");
        TreatmentRoom roomB = newRoom("B실");
        TreatmentSeat seatA1 = newSeat(roomA.getId(), "1");

        String date = "2026-07-01";
        register(roomA.getId(), seatA1.getId(), "환자자리있음", date); // room A, seat
        register(roomA.getId(), null, "환자자리없음", date);          // room A, no seat
        register(roomB.getId(), null, "다른방환자", date);            // room B

        List<TreatmentSchedule> roomAList =
                scheduleService.listSchedules("q", date, null, null, null, roomA.getId());

        // roomId 필터 → A실 2건만
        assertThat(roomAList).hasSize(2);
        assertThat(roomAList).extracting(TreatmentSchedule::getPatientName)
                .containsExactlyInAnyOrder("환자자리있음", "환자자리없음");

        TreatmentSchedule withSeat = roomAList.stream()
                .filter(s -> "환자자리있음".equals(s.getPatientName())).findFirst().orElseThrow();
        TreatmentSchedule noSeat = roomAList.stream()
                .filter(s -> "환자자리없음".equals(s.getPatientName())).findFirst().orElseThrow();

        assertThat(withSeat.getSeatName()).isEqualTo("1자리");   // JOIN ct_treatment_seat
        assertThat(withSeat.getSeatId()).isEqualTo(seatA1.getId());
        assertThat(noSeat.getSeatId()).isNull();                 // 미지정 → NULL
        assertThat(noSeat.getSeatName()).isNull();
    }
}
