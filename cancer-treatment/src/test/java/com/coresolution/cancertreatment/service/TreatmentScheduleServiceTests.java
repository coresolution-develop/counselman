package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void updateStartTimeAcceptsHhMmAndNormalizes() {
        TreatmentScheduleService service = new TreatmentScheduleService();

        TreatmentSchedule updated = service.updateStartTime(1L, "9:05");

        assertThat(updated.getStartTime()).isEqualTo("09:05");
    }

    @Test
    void updateStartTimeRejectsInvalidFormat() {
        TreatmentScheduleService service = new TreatmentScheduleService();

        assertThatThrownBy(() -> service.updateStartTime(1L, "25:99"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateStartTime(1L, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStartTimeRejectsUnknownId() {
        TreatmentScheduleService service = new TreatmentScheduleService();

        assertThatThrownBy(() -> service.updateStartTime(99999L, "10:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
