package com.coresolution.cancertreatment.model;

public class TreatmentScheduleRequest {

    private String treatmentDate;
    private String startTime;
    private String patientName;
    private String ward;
    private String treatmentName;
    private String treatmentOption;
    private String status;
    private String treatmentInfo;
    private String note;

    public String getTreatmentDate() {
        return treatmentDate;
    }

    public void setTreatmentDate(String treatmentDate) {
        this.treatmentDate = treatmentDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getTreatmentName() {
        return treatmentName;
    }

    public void setTreatmentName(String treatmentName) {
        this.treatmentName = treatmentName;
    }

    public String getTreatmentOption() {
        return treatmentOption;
    }

    public void setTreatmentOption(String treatmentOption) {
        this.treatmentOption = treatmentOption;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTreatmentInfo() {
        return treatmentInfo;
    }

    public void setTreatmentInfo(String treatmentInfo) {
        this.treatmentInfo = treatmentInfo;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
