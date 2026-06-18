package com.coresolution.cancertreatment.model;

public class TreatmentTypeDuration {

    private final Long id;
    private final String treatmentName;
    private final String roomName;
    private final Integer durationMinutes;

    public TreatmentTypeDuration(Long id, String treatmentName, String roomName, Integer durationMinutes) {
        this.id = id;
        this.treatmentName = treatmentName;
        this.roomName = roomName;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getTreatmentName() {
        return treatmentName;
    }

    public String getRoomName() {
        return roomName;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }
}
