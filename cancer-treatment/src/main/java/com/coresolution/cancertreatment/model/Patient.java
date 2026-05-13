package com.coresolution.cancertreatment.model;

import java.util.List;

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
    private final Integer prescriptionWeeks;
    private final Integer copaymentRate;
    private final String totalDiscountType;
    private final Integer totalDiscountValue;
    private final List<Long> prescriptionItemIds;

    public Patient(
            Long id,
            String name,
            String chartNo,
            String room,
            String ward,
            String admissionDate,
            String dischargeDate,
            String treatmentInfo,
            String note,
            Integer prescriptionWeeks,
            Integer copaymentRate,
            String totalDiscountType,
            Integer totalDiscountValue,
            List<Long> prescriptionItemIds) {
        this.id = id;
        this.name = name;
        this.chartNo = chartNo;
        this.room = room;
        this.ward = ward;
        this.admissionDate = admissionDate;
        this.dischargeDate = dischargeDate;
        this.treatmentInfo = treatmentInfo;
        this.note = note;
        this.prescriptionWeeks = prescriptionWeeks;
        this.copaymentRate = copaymentRate;
        this.totalDiscountType = totalDiscountType;
        this.totalDiscountValue = totalDiscountValue;
        this.prescriptionItemIds = prescriptionItemIds == null ? List.of() : List.copyOf(prescriptionItemIds);
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

    public Integer getPrescriptionWeeks() {
        return prescriptionWeeks;
    }

    public Integer getCopaymentRate() {
        return copaymentRate;
    }

    public String getTotalDiscountType() {
        return totalDiscountType;
    }

    public Integer getTotalDiscountValue() {
        return totalDiscountValue;
    }

    public List<Long> getPrescriptionItemIds() {
        return prescriptionItemIds;
    }
}
