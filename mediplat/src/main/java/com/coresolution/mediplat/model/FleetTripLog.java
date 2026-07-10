package com.coresolution.mediplat.model;

import java.time.LocalDateTime;

/**
 * 운행 기록. 출발/도착 2단계로 채워지며 세무 운행기록부의 원천 데이터다.
 *
 * <p>{@code driverUsername}은 운행 시점의 자연키 스냅샷으로, 로스터가 이후 수정돼도 증빙이
 * 흔들리지 않도록 비정규화해 저장한다. 계기판 사진 경로는 출발 시 필수({@code startPhotoPath}).
 */
public class FleetTripLog {
    private final Long id;
    private final String instCode;
    private final Long vehicleId;
    private final Long driverId;
    private final String driverUsername;
    private final String purposeCode;
    private final String purposeMemo;
    private final LocalDateTime departAt;
    private final LocalDateTime arriveAt;
    private final Integer odometerStart;
    private final Integer odometerEnd;
    private final Integer distance;
    private final String odometerStartSrc;
    private final String odometerEndSrc;
    private final String startPhotoPath;
    private final String endPhotoPath;
    private final String statusCode;
    private final String overLimitYn;
    private final LocalDateTime createdAt;

    public FleetTripLog(
            Long id,
            String instCode,
            Long vehicleId,
            Long driverId,
            String driverUsername,
            String purposeCode,
            String purposeMemo,
            LocalDateTime departAt,
            LocalDateTime arriveAt,
            Integer odometerStart,
            Integer odometerEnd,
            Integer distance,
            String odometerStartSrc,
            String odometerEndSrc,
            String startPhotoPath,
            String endPhotoPath,
            String statusCode,
            String overLimitYn,
            LocalDateTime createdAt) {
        this.id = id;
        this.instCode = instCode;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.driverUsername = driverUsername;
        this.purposeCode = purposeCode;
        this.purposeMemo = purposeMemo;
        this.departAt = departAt;
        this.arriveAt = arriveAt;
        this.odometerStart = odometerStart;
        this.odometerEnd = odometerEnd;
        this.distance = distance;
        this.odometerStartSrc = odometerStartSrc;
        this.odometerEndSrc = odometerEndSrc;
        this.startPhotoPath = startPhotoPath;
        this.endPhotoPath = endPhotoPath;
        this.statusCode = statusCode;
        this.overLimitYn = overLimitYn;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getDriverUsername() {
        return driverUsername;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public String getPurposeMemo() {
        return purposeMemo;
    }

    public LocalDateTime getDepartAt() {
        return departAt;
    }

    public LocalDateTime getArriveAt() {
        return arriveAt;
    }

    public Integer getOdometerStart() {
        return odometerStart;
    }

    public Integer getOdometerEnd() {
        return odometerEnd;
    }

    public Integer getDistance() {
        return distance;
    }

    public String getOdometerStartSrc() {
        return odometerStartSrc;
    }

    public String getOdometerEndSrc() {
        return odometerEndSrc;
    }

    public String getStartPhotoPath() {
        return startPhotoPath;
    }

    public String getEndPhotoPath() {
        return endPhotoPath;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getOverLimitYn() {
        return overLimitYn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isOngoing() {
        return "ONGOING".equalsIgnoreCase(statusCode);
    }

    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(statusCode);
    }

    public boolean isOverLimit() {
        return "Y".equalsIgnoreCase(overLimitYn);
    }
}
