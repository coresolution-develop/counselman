package com.coresolution.mediplat.model;

import java.time.LocalDateTime;

public class SeminarNotification {
    private final Long id;
    private final Long reservationId;
    private final String instCode;
    private final String managerUsername;
    private final String message;
    private final String readYn;
    private final LocalDateTime createdAt;

    public SeminarNotification(
            Long id,
            Long reservationId,
            String instCode,
            String managerUsername,
            String message,
            String readYn,
            LocalDateTime createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.instCode = instCode;
        this.managerUsername = managerUsername;
        this.message = message;
        this.readYn = readYn;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getManagerUsername() {
        return managerUsername;
    }

    public String getMessage() {
        return message;
    }

    public String getReadYn() {
        return readYn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return "Y".equalsIgnoreCase(readYn);
    }
}
