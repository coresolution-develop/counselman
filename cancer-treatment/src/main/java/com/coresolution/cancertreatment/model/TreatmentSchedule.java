package com.coresolution.cancertreatment.model;

public class TreatmentSchedule {

    private final Long id;
    private final String treatmentDate;
    private final String startTime;
    private final String patientName;
    private final String ward;
    private final String treatmentName;
    private final String treatmentOption;
    private String status;
    private String treatmentInfo;
    private String note;

    public TreatmentSchedule(
            Long id,
            String treatmentDate,
            String startTime,
            String patientName,
            String ward,
            String treatmentName,
            String treatmentOption,
            String status,
            String treatmentInfo,
            String note) {
        this.id = id;
        this.treatmentDate = treatmentDate;
        this.startTime = startTime;
        this.patientName = patientName;
        this.ward = ward;
        this.treatmentName = treatmentName;
        this.treatmentOption = treatmentOption;
        this.status = status;
        this.treatmentInfo = treatmentInfo;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getTreatmentDate() {
        return treatmentDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getWard() {
        return ward;
    }

    public String getTreatmentName() {
        return treatmentName;
    }

    public String getTreatmentOption() {
        return treatmentOption;
    }

    public String getStatus() {
        return status;
    }

    public String getTreatmentInfo() {
        return treatmentInfo;
    }

    public String getNote() {
        return note;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTreatmentInfo(String treatmentInfo) {
        this.treatmentInfo = treatmentInfo;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
