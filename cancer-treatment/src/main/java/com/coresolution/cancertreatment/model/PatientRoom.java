package com.coresolution.cancertreatment.model;

public class PatientRoom {

    private final Long id;
    private final String roomCode;
    private final String roomName;
    private final Long wardId;
    private final String wardName;
    private final String admissionType;
    private final Integer displayOrder;

    public PatientRoom(
            Long id,
            String roomCode,
            String roomName,
            Long wardId,
            String wardName,
            String admissionType,
            Integer displayOrder) {
        this.id = id;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.wardId = wardId;
        this.wardName = wardName;
        this.admissionType = admissionType;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getRoomName() {
        return roomName;
    }

    public Long getWardId() {
        return wardId;
    }

    public String getWardName() {
        return wardName;
    }

    public String getAdmissionType() {
        return admissionType;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
