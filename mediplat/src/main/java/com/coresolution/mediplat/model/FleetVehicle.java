package com.coresolution.mediplat.model;

import java.time.LocalDateTime;

public class FleetVehicle {
    private final Long id;
    private final String instCode;
    private final String plateNo;
    private final String name;
    private final String modelName;
    private final String department;
    private final String statusCode;
    private final Integer currentOdometer;
    private final String qrToken;
    private final String useYn;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public FleetVehicle(
            Long id,
            String instCode,
            String plateNo,
            String name,
            String modelName,
            String department,
            String statusCode,
            Integer currentOdometer,
            String qrToken,
            String useYn,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.instCode = instCode;
        this.plateNo = plateNo;
        this.name = name;
        this.modelName = modelName;
        this.department = department;
        this.statusCode = statusCode;
        this.currentOdometer = currentOdometer;
        this.qrToken = qrToken;
        this.useYn = useYn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public String getName() {
        return name;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDepartment() {
        return department;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public Integer getCurrentOdometer() {
        return currentOdometer;
    }

    public String getQrToken() {
        return qrToken;
    }

    public String getUseYn() {
        return useYn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEnabled() {
        return "Y".equalsIgnoreCase(useYn);
    }

    /** 운행 시작 가능 상태(가용). */
    public boolean isAvailable() {
        return isEnabled() && "IDLE".equalsIgnoreCase(statusCode);
    }

    public boolean isRunning() {
        return "RUNNING".equalsIgnoreCase(statusCode);
    }
}
