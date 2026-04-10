package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class RoomBoardSnapshot {
    private Long id;
    private String sourceType;
    private String snapshotDate;
    private String snapshotTime;
    private String rawText;
    private String uploadedBy;
    private String uploadedAt;
    private String parseStatus;
    private String parseMessage;
}
