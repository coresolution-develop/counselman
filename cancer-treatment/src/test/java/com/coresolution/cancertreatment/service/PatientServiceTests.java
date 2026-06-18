package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.Patient;
import com.coresolution.cancertreatment.model.PatientRequest;

@SpringBootTest
class PatientServiceTests {

    @Autowired
    private PatientService patientService;

    @Test
    void createPatientThenListPatients() {
        PatientRequest request = new PatientRequest();
        request.setName("등록환자");
        request.setChartNo("CT-1001");
        request.setRoom("201");
        request.setWard("1병동");
        request.setAdmissionDate("2026-05-12");
        request.setTreatmentInfo("싸이 주2");
        request.setNote("테스트 등록");

        Patient created = patientService.createPatient("core", request);
        List<Patient> patients = patientService.listPatients("core", "CT-1001", "1병동");

        assertThat(created.getId()).isNotNull();
        assertThat(patients)
                .extracting(Patient::getName)
                .contains("등록환자");
    }

    @Test
    void updatePatientTreatmentInfoField() {
        PatientRequest request = new PatientRequest();
        request.setName("수정환자");
        request.setChartNo("CT-1002");
        request.setRoom("202");
        request.setWard("1병동");
        request.setAdmissionDate("2026-05-12");
        request.setTreatmentInfo("파동 주1");

        Patient created = patientService.createPatient("core", request);
        Patient updated = patientService.updateTextField("core", created.getId(), "treatmentInfo", "파동 주3 + 림프");

        assertThat(updated.getTreatmentInfo()).isEqualTo("파동 주3 + 림프");
    }

    @Test
    void createPatientPersistsTreatmentStartDate() {
        PatientRequest request = new PatientRequest();
        request.setName("치료시작환자");
        request.setChartNo("CT-1003");
        request.setWard("1병동");
        request.setAdmissionDate("2026-05-12");
        request.setTreatmentStartDate("2026-05-14");

        Patient created = patientService.createPatient("core", request);

        assertThat(created.getTreatmentStartDate()).isEqualTo("2026-05-14");
    }

    @Test
    void rejectsTreatmentStartBeforeAdmission() {
        PatientRequest request = new PatientRequest();
        request.setName("역전환자");
        request.setAdmissionDate("2026-05-12");
        request.setTreatmentStartDate("2026-05-10");

        assertThatThrownBy(() -> patientService.createPatient("core", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("치료 시작일은 입원일보다");
    }
}
