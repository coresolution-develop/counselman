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
    private String note;
    private List<String> patientSlots = new ArrayList<>();
}
