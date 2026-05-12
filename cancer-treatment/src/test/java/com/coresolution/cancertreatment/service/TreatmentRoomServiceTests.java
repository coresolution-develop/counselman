package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.TreatmentRoom;
import com.coresolution.cancertreatment.model.TreatmentRoomRequest;

@SpringBootTest
class TreatmentRoomServiceTests {

    @Autowired
    private TreatmentRoomService treatmentRoomService;

    @Test
    void createUpdateAndDeleteTreatmentRoom() {
        TreatmentRoomRequest createRequest = new TreatmentRoomRequest();
        createRequest.setManagementNo("4-TEST");
        createRequest.setRoomName("테스트치료실");
        createRequest.setTreatmentItems(List.of("BSD", "EHY1", "EHY2", "통원"));
        createRequest.setManagerName("담당자");
        createRequest.setDisplayOrder(10);

        TreatmentRoom created = treatmentRoomService.createRoom("core", createRequest);

        TreatmentRoomRequest updateRequest = new TreatmentRoomRequest();
        updateRequest.setManagementNo("4-TEST");
        updateRequest.setRoomName("테스트치료실수정");
        updateRequest.setTreatmentItems(List.of("BSD", "통원"));
        updateRequest.setManagerName("담당자");
        updateRequest.setDisplayOrder(11);
        TreatmentRoom updated = treatmentRoomService.updateRoom("core", created.getId(), updateRequest);

        treatmentRoomService.deleteRoom("core", updated.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(updated.getRoomName()).isEqualTo("테스트치료실수정");
        assertThat(updated.getTreatmentItems()).containsExactly("BSD", "통원");
        assertThat(treatmentRoomService.listRooms("core"))
                .extracting(TreatmentRoom::getId)
                .doesNotContain(updated.getId());
    }

    @Test
    void createTreatmentRoomWithoutManagementNo() {
        TreatmentRoomRequest request = new TreatmentRoomRequest();
        request.setRoomName("관리번호없는치료실");
        request.setTreatmentItems(List.of("림프"));
        request.setManagerName("담당자");

        TreatmentRoom created = treatmentRoomService.createRoom("core", request);
        treatmentRoomService.deleteRoom("core", created.getId());

        assertThat(created.getManagementNo()).isNullOrEmpty();
    }

    @Test
    void createTreatmentRoomWithoutTreatmentItems() {
        TreatmentRoomRequest request = new TreatmentRoomRequest();
        request.setRoomName("치료항목없는치료실");

        TreatmentRoom created = treatmentRoomService.createRoom("core", request);
        treatmentRoomService.deleteRoom("core", created.getId());

        assertThat(created.getTreatmentItems()).isEmpty();
    }
}
