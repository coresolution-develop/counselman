package com.coresolution.mediplat.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class SeminarReservation {
    private final Long id;
    private final String instCode;
    private final Long seminarId;
    private final String seminarName;
    private final String requesterUsername;
    private final String requesterName;
    private final LocalDate reservationDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final Integer attendeeCount;
    private final String usedItems;
    private final String neededItems;
    private final String purpose;
    private final String statusCode;
    private final String managerNote;
    private final LocalDateTime createdAt;

    public SeminarReservation(
            Long id,
            String instCode,
            Long seminarId,
            String seminarName,
            String requesterUsername,
            String requesterName,
            LocalDate reservationDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer attendeeCount,
            String usedItems,
            String neededItems,
            String purpose,
            String statusCode,
            String managerNote,
            LocalDateTime createdAt) {
        this.id = id;
        this.instCode = instCode;
        this.seminarId = seminarId;
        this.seminarName = seminarName;
        this.requesterUsername = requesterUsername;
        this.requesterName = requesterName;
        this.reservationDate = reservationDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attendeeCount = attendeeCount;
        this.usedItems = usedItems;
        this.neededItems = neededItems;
        this.purpose = purpose;
        this.statusCode = statusCode;
        this.managerNote = managerNote;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public Long getSeminarId() {
        return seminarId;
    }

    public String getSeminarName() {
        return seminarName;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getAttendeeCount() {
        return attendeeCount;
    }

    public String getUsedItems() {
        return usedItems;
    }

    public String getNeededItems() {
        return neededItems;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getManagerNote() {
        return managerNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(statusCode);
    }
}
