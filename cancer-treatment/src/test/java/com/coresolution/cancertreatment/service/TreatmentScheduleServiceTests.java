package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.coresolution.cancertreatment.model.TreatmentSchedule;

class TreatmentScheduleServiceTests {

    @Test
    void updateTreatmentInfoAndNote() {
        TreatmentScheduleService service = new TreatmentScheduleService();

        TreatmentSchedule infoUpdated = service.updateTextField(1L, "treatmentInfo", "수정된 치료정보");
        TreatmentSchedule noteUpdated = service.updateTextField(1L, "note", "수정된 참고사항");

        assertThat(infoUpdated.getTreatmentInfo()).isEqualTo("수정된 치료정보");
        assertThat(noteUpdated.getNote()).isEqualTo("수정된 참고사항");
    }
}
