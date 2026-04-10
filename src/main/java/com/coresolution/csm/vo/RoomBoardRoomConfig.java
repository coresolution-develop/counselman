package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class RoomBoardRoomConfig {
    private Long id;
    private String wardName;
    private String roomName;
    private String startDate;
    private String endDate;
    private Integer licensedBeds;
    private String careType;
    private String statusWalk;
    private String statusDiaper;
    private String statusOxygen;
    private String statusSuction;
    private String nursingCost;
    private String note;
    private String useYn;
    private String createdAt;
    private String createdBy;
    private String updatedAt;
    private String updatedBy;

    public String getStatusLabel() {
        StringBuilder sb = new StringBuilder();
        appendStatus(sb, statusWalk, "거동");
        appendStatus(sb, statusDiaper, "기저귀");
        appendStatus(sb, statusOxygen, "산소");
        appendStatus(sb, statusSuction, "석션");
        return sb.toString();
    }

    private void appendStatus(StringBuilder sb, String value, String label) {
        if (!"Y".equalsIgnoreCase(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(label);
    }
}
