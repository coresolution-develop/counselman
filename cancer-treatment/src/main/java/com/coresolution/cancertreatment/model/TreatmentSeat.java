package com.coresolution.cancertreatment.model;

public class TreatmentSeat {

    private final Long id;
    private final Long treatmentRoomId;
    private final String treatmentRoomName;
    private final String seatCode;
    private final String seatName;
    private final Integer displayOrder;

    public TreatmentSeat(
            Long id,
            Long treatmentRoomId,
            String treatmentRoomName,
            String seatCode,
            String seatName,
            Integer displayOrder) {
        this.id = id;
        this.treatmentRoomId = treatmentRoomId;
        this.treatmentRoomName = treatmentRoomName;
        this.seatCode = seatCode;
        this.seatName = seatName;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getTreatmentRoomId() {
        return treatmentRoomId;
    }

    public String getTreatmentRoomName() {
        return treatmentRoomName;
    }

    public String getSeatCode() {
        return seatCode;
    }

    public String getSeatName() {
        return seatName;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
