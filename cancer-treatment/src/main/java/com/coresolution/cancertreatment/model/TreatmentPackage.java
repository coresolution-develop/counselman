package com.coresolution.cancertreatment.model;

public class TreatmentPackage {

    private final Long id;
    private final Long categoryId;
    private final String categoryName;
    private final Long treatmentRoomId;
    private final String treatmentRoomName;
    private final String packageName;
    private final String abbreviation;
    private final Integer unitPrice;
    private final String billingUnit;
    private final Integer frequency;
    private final Integer displayOrder;

    public TreatmentPackage(
            Long id,
            Long categoryId,
            String categoryName,
            Long treatmentRoomId,
            String treatmentRoomName,
            String packageName,
            String abbreviation,
            Integer unitPrice,
            String billingUnit,
            Integer frequency,
            Integer displayOrder) {
        this.id = id;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.treatmentRoomId = treatmentRoomId;
        this.treatmentRoomName = treatmentRoomName;
        this.packageName = packageName;
        this.abbreviation = abbreviation;
        this.unitPrice = unitPrice;
        this.billingUnit = billingUnit;
        this.frequency = frequency;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Long getTreatmentRoomId() {
        return treatmentRoomId;
    }

    public String getTreatmentRoomName() {
        return treatmentRoomName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public Integer getUnitPrice() {
        return unitPrice;
    }

    public String getBillingUnit() {
        return billingUnit;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
