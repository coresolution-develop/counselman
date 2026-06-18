package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.PatientRoom;
import com.coresolution.cancertreatment.model.PatientRoomRequest;
import com.coresolution.cancertreatment.model.SettingItem;
import com.coresolution.cancertreatment.model.SettingItemRequest;

@SpringBootTest
class PatientRoomServiceTests {

    @Autowired
    private PatientRoomService patientRoomService;

    @Autowired
    private SettingService settingService;

    @Test
    void createRoomDerivesAdmissionTypeFromWard() {
        SettingItemRequest wardRequest = new SettingItemRequest();
        wardRequest.setCode("WARD-OPD");
        wardRequest.setName("외래");
        wardRequest.setDetail("OUTPATIENT");
        SettingItem ward = settingService.createItem("core", "wards", wardRequest);

        PatientRoomRequest request = new PatientRoomRequest();
        request.setRoomCode("OPD-1");
        request.setRoomName("외래1");
        request.setWardId(ward.getId());
        patientRoomService.createRoom("core", request);

        List<PatientRoom> rooms = patientRoomService.listRooms("core");
        PatientRoom opd = rooms.stream()
                .filter(r -> "OPD-1".equals(r.getRoomCode()))
                .findFirst()
                .orElseThrow();
        assertThat(opd.getWardName()).isEqualTo("외래");
        assertThat(opd.getAdmissionType()).isEqualTo("OUTPATIENT");
    }

    @Test
    void rejectsDuplicateRoomCode() {
        PatientRoomRequest first = new PatientRoomRequest();
        first.setRoomCode("DUP-1");
        first.setRoomName("중복1");
        patientRoomService.createRoom("core", first);

        PatientRoomRequest second = new PatientRoomRequest();
        second.setRoomCode("DUP-1");
        second.setRoomName("중복2");
        assertThatThrownBy(() -> patientRoomService.createRoom("core", second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 병실");
    }
}
