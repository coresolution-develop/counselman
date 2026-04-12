package com.coresolution.csm.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class RoomBoardRoomConfigPasteResult {
    private int parsedCount;
    private int skippedCount;
    private String message;
    private List<RoomBoardRoomConfig> rows = new ArrayList<>();
}
