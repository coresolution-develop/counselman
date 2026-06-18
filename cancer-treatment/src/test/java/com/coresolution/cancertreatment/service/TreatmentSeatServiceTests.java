package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.TreatmentRoom;
import com.coresolution.cancertreatment.model.TreatmentRoomRequest;
import com.coresolution.cancertreatment.model.TreatmentSeat;
import com.coresolution.cancertreatment.model.TreatmentSeatRequest;

@SpringBootTest
class TreatmentSeatServiceTests {

    @Autowired
    private TreatmentSeatService treatmentSeatService;

    @Autowired
    private TreatmentRoomService treatmentRoomService;

    @Test
    void createSeatJoinsRoomName() {
        TreatmentRoomRequest roomRequest = new TreatmentRoomRequest();
        roomRequest.setRoomName("고주파치료실");
        TreatmentRoom room = treatmentRoomService.createRoom("core", roomRequest);

        TreatmentSeatRequest seatRequest = new TreatmentSeatRequest();
        seatRequest.setTreatmentRoomId(room.getId());
        seatRequest.setSeatCode("A");
        seatRequest.setSeatName("A자리");
        treatmentSeatService.createSeat("core", seatRequest);

        List<TreatmentSeat> seats = treatmentSeatService.listSeats("core", room.getId());
        assertThat(seats).hasSize(1);
        assertThat(seats.get(0).getSeatCode()).isEqualTo("A");
        assertThat(seats.get(0).getTreatmentRoomName()).isEqualTo("고주파치료실");
    }

    @Test
    void rejectsDuplicateSeatCodeInSameRoom() {
        TreatmentRoomRequest roomRequest = new TreatmentRoomRequest();
        roomRequest.setRoomName("도수치료실");
        TreatmentRoom room = treatmentRoomService.createRoom("core", roomRequest);

        TreatmentSeatRequest first = new TreatmentSeatRequest();
        first.setTreatmentRoomId(room.getId());
        first.setSeatCode("1");
        first.setSeatName("1번");
        treatmentSeatService.createSeat("core", first);

        TreatmentSeatRequest dup = new TreatmentSeatRequest();
        dup.setTreatmentRoomId(room.getId());
        dup.setSeatCode("1");
        dup.setSeatName("중복");
        assertThatThrownBy(() -> treatmentSeatService.createSeat("core", dup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 자리");
    }
}
