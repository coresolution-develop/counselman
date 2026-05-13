package com.coresolution.cancertreatment.model;

import java.util.List;

public class PatientRequest {

    private String name;
    private String chartNo;
    private String room;
    private String ward;
    private String admissionDate;
    private String dischargeDate;
    private String treatmentInfo;
    private String note;
    private Integer prescriptionWeeks;
    private Integer copaymentRate;
    private String totalDiscountType;
    private Integer totalDiscountValue;
    private List<Long> prescriptionItemIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getChartNo() {
        return chartNo;
    }

    public void setChartNo(String chartNo) {
        this.chartNo = chartNo;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(String admissionDate) {
        this.admissionDate = admissionDate;
    }

    public String getDischargeDate() {
        return dischargeDate;
    }

    public void setDischargeDate(String dischargeDate) {
        this.dischargeDate = dischargeDate;
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

    public Integer getPrescriptionWeeks() {
        return prescriptionWeeks;
    }

    public void setPrescriptionWeeks(Integer prescriptionWeeks) {
        this.prescriptionWeeks = prescriptionWeeks;
    }

    public Integer getCopaymentRate() {
        return copaymentRate;
    }

    public void setCopaymentRate(Integer copaymentRate) {
        this.copaymentRate = copaymentRate;
    }

    public String getTotalDiscountType() {
        return totalDiscountType;
    }

    public void setTotalDiscountType(String totalDiscountType) {
        this.totalDiscountType = totalDiscountType;
    }

    public Integer getTotalDiscountValue() {
        return totalDiscountValue;
    }

    public void setTotalDiscountValue(Integer totalDiscountValue) {
        this.totalDiscountValue = totalDiscountValue;
    }

    public List<Long> getPrescriptionItemIds() {
        return prescriptionItemIds;
    }

    public void setPrescriptionItemIds(List<Long> prescriptionItemIds) {
        this.prescriptionItemIds = prescriptionItemIds;
    }
}
