package com.coresolution.cancertreatment.model;

public class TreatmentSchedule {

    private final Long id;
    private final Long patientId;
    private final String treatmentDate;
    private String startTime;
    private final String patientName;
    private final String ward;
    private final String treatmentName;
    private final String treatmentOption;
    private String status;
    private String treatmentInfo;
    private String note;
    private final Long treatmentRoomId;
    private final Long seatId;
    private final String seatName;
    private final String attendingDoctor;

    public TreatmentSchedule(
            Long id,
            Long patientId,
            String treatmentDate,
            String startTime,
            String patientName,
            String ward,
            String treatmentName,
            String treatmentOption,
            String status,
            String treatmentInfo,
            String note,
            Long treatmentRoomId,
            Long seatId,
            String seatName,
            String attendingDoctor) {
        this.id = id;
        this.patientId = patientId;
        this.treatmentDate = treatmentDate;
        this.startTime = startTime;
        this.patientName = patientName;
        this.ward = ward;
        this.treatmentName = treatmentName;
        this.treatmentOption = treatmentOption;
        this.status = status;
        this.treatmentInfo = treatmentInfo;
        this.note = note;
        this.treatmentRoomId = treatmentRoomId;
        this.seatId = seatId;
        this.seatName = seatName;
        this.attendingDoctor = attendingDoctor;
    }

    public String getAttendingDoctor() {
        return attendingDoctor;
    }

    public Long getTreatmentRoomId() {
        return treatmentRoomId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public String getSeatName() {
        return seatName;
    }

    public Long getId() {
        return id;
    }

    public Long getPatientId() {
        return patientId;
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

    public void setStartTime(String startTime) {
        this.startTime = startTime;
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
