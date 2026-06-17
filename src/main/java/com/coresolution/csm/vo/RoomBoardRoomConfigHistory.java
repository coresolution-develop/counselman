package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class RoomBoardRoomConfigHistory {
    private Long id;
    private Long rbmId;
    private String action;
    private String wardName;
    private String roomName;
    private String startDate;
    private String endDate;
    private Integer licensedBeds;
    private Integer availableBeds;
    private String roomGender;
    private String careType;
    private String statusWalk;
    private String statusDiaper;
    private String statusOxygen;
    private String statusSuction;
    private String nursingCost;
    private String note;
    private String useYn;
    private String changedBy;
    private String changedAt;

    public String getActionLabel() {
        if (action == null) {
            return "";
        }
        switch (action) {
            case "CREATE":
                return "생성";
            case "UPDATE":
                return "수정";
            case "DELETE":
                return "삭제";
            default:
                return action;
        }
    }

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
