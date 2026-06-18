package com.coresolution.cancertreatment.model;

public class TreatmentSeatRequest {

    private Long treatmentRoomId;
    private String seatCode;
    private String seatName;
    private Integer displayOrder;

    public Long getTreatmentRoomId() {
        return treatmentRoomId;
    }

    public void setTreatmentRoomId(Long treatmentRoomId) {
        this.treatmentRoomId = treatmentRoomId;
    }

    public String getSeatCode() {
        return seatCode;
    }

    public void setSeatCode(String seatCode) {
        this.seatCode = seatCode;
    }

    public String getSeatName() {
        return seatName;
    }

    public void setSeatName(String seatName) {
        this.seatName = seatName;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
