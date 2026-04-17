package com.coresolution.mediplat.model;

public class SeminarRoom {
    private final Long id;
    private final String instCode;
    private final String seminarName;
    private final String roomName;
    private final Integer capacity;
    private final String useYn;

    public SeminarRoom(
            Long id,
            String instCode,
            String seminarName,
            String roomName,
            Integer capacity,
            String useYn) {
        this.id = id;
        this.instCode = instCode;
        this.seminarName = seminarName;
        this.roomName = roomName;
        this.capacity = capacity;
        this.useYn = useYn;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getSeminarName() {
        return seminarName;
    }

    public String getRoomName() {
        return roomName;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public String getUseYn() {
        return useYn;
    }

    public boolean isEnabled() {
        return "Y".equalsIgnoreCase(useYn);
    }
}
