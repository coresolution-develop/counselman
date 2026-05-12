package com.coresolution.cancertreatment.model;

public class Patient {

    private final Long id;
    private final String name;
    private final String chartNo;
    private final String room;
    private final String ward;
    private final String admissionDate;
    private final String dischargeDate;
    private final String treatmentInfo;
    private final String note;

    public Patient(
            Long id,
            String name,
            String chartNo,
            String room,
            String ward,
            String admissionDate,
            String dischargeDate,
            String treatmentInfo,
            String note) {
        this.id = id;
        this.name = name;
        this.chartNo = chartNo;
        this.room = room;
        this.ward = ward;
        this.admissionDate = admissionDate;
        this.dischargeDate = dischargeDate;
        this.treatmentInfo = treatmentInfo;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getChartNo() {
        return chartNo;
    }

    public String getRoom() {
        return room;
    }

    public String getWard() {
        return ward;
    }

    public String getAdmissionDate() {
        return admissionDate;
    }

    public String getDischargeDate() {
        return dischargeDate;
    }

    public String getTreatmentInfo() {
        return treatmentInfo;
    }

    public String getNote() {
        return note;
    }
}
