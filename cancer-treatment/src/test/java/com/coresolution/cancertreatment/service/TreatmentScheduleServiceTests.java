package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.Patient;
import com.coresolution.cancertreatment.model.PatientRequest;
import com.coresolution.cancertreatment.model.TreatmentSchedule;
import com.coresolution.cancertreatment.model.TreatmentScheduleRequest;

@SpringBootTest
class TreatmentScheduleServiceTests {

    @Autowired
    private TreatmentScheduleService scheduleService;

    @Autowired
    private PatientService patientService;

    @Test
    void createThenListPersistsScopedByInst() {
        String date = "2026-07-01";
        scheduleService.createSchedule("core", request(null, date, "09:00", "홍길동", "1병동", "파동", "예약"));

        List<TreatmentSchedule> coreItems = scheduleService.listSchedules("core", date, null, null, null);
        List<TreatmentSchedule> otherItems = scheduleService.listSchedules("OTHER", date, null, null, null);

        assertThat(coreItems).extracting(TreatmentSchedule::getPatientName).contains("홍길동");
        assertThat(otherItems).isEmpty(); // tenant isolation
    }

    @Test
    void statusRoundTripsThroughKoreanLabels() {
        String date = "2026-07-02";
        TreatmentSchedule created =
                scheduleService.createSchedule("core", request(null, date, "10:00", "김완료", "1병동", "고주파", "치료완료"));

        assertThat(created.getStatus()).isEqualTo("치료완료");
        assertThat(scheduleService.listSchedules("core", date, null, null, null))
                .extracting(TreatmentSchedule::getStatus)
                .contains("치료완료");
    }

    @Test
    void linkedPatientNameReflectsLatestPatientRecord() {
        String date = "2026-07-03";
        PatientRequest p = new PatientRequest();
        p.setName("원래이름");
        p.setWard("1병동");
        Patient patient = patientService.createPatient("core", p);

        scheduleService.createSchedule("core", request(patient.getId(), date, "11:00", "원래이름", "1병동", "도수", "예약"));
        patientService.updateTextField("core", patient.getId(), "name", "변경이름");

        TreatmentSchedule reloaded = scheduleService.listSchedules("core", date, null, null, null).get(0);
        assertThat(reloaded.getPatientName()).isEqualTo("변경이름"); // always-latest via live join
        assertThat(reloaded.getPatientId()).isEqualTo(patient.getId());
    }

    @Test
    void freeTextScheduleFallsBackToSnapshotName() {
        String date = "2026-07-04";
        scheduleService.createSchedule("core", request(null, date, "12:00", "자유입력환자", "외래", "림프", "예약"));

        TreatmentSchedule reloaded = scheduleService.listSchedules("core", date, null, null, null).get(0);
        assertThat(reloaded.getPatientName()).isEqualTo("자유입력환자");
        assertThat(reloaded.getPatientId()).isNull();
    }

    @Test
    void updateStartTimeNormalizesAndPersists() {
        String date = "2026-07-05";
        TreatmentSchedule created =
                scheduleService.createSchedule("core", request(null, date, "13:00", "박시간", "1병동", "파동", "예약"));

        TreatmentSchedule updated = scheduleService.updateStartTime("core", created.getId(), "9:05");

        assertThat(updated.getStartTime()).isEqualTo("09:05");
    }

    @Test
    void updateStartTimeRejectsInvalidFormat() {
        assertThatThrownBy(() -> scheduleService.updateStartTime("core", 1L, "25:99"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduleService.updateStartTime("core", 1L, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesSchedule() {
        String date = "2026-07-06";
        TreatmentSchedule created =
                scheduleService.createSchedule("core", request(null, date, "14:00", "삭제대상", "1병동", "파동", "예약"));

        scheduleService.deleteSchedule("core", created.getId());

        assertThat(scheduleService.listSchedules("core", date, null, null, null)).isEmpty();
    }

    private TreatmentScheduleRequest request(
            Long patientId, String date, String time, String patientName, String ward, String treatment, String status) {
        TreatmentScheduleRequest req = new TreatmentScheduleRequest();
        req.setPatientId(patientId);
        req.setTreatmentDate(date);
        req.setStartTime(time);
        req.setPatientName(patientName);
        req.setWard(ward);
        req.setTreatmentName(treatment);
        req.setStatus(status);
        return req;
    }
}
