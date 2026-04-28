package com.coresolution.csm.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class RoomBoardRoomView {
    private String wardName;
    private String roomName;
    private Integer licensedBeds;
    private String genderSummary;
    private Integer occupiedCount;
    private Integer availableCount;
    private Integer reservationCount;
    private Double occupancyRate;
    private String careType;
    private String reservationNames;
    private String statusLabel;
    private Integer dischargeNoticeCount;
    private Integer afternoonAvailableCount;
    private String dischargePatientNames;
    private boolean statusWalk;
    private boolean statusDiaper;
    private boolean statusOxygen;
    private boolean statusSuction;
    private String note;
    private List<String> patientSlots = new ArrayList<>();
    private List<String> dischargeSlotLabels = new ArrayList<>();
    private List<String> dischargeSlotAvailability = new ArrayList<>();
}
