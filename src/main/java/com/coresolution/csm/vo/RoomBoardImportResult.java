package com.coresolution.csm.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class RoomBoardImportResult {
    private String sourceType;
    private String snapshotDate;
    private String snapshotTime;
    private int parsedCount;
    private int skippedCount;
    private String message;
    private Long snapshotId;
    private List<RoomBoardImportRow> rows = new ArrayList<>();
}
