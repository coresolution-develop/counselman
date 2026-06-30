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
    /** 이미 활성이거나 기간이 겹치는 병실 목록 — 비어있지 않으면 저장이 차단된다. */
    private List<String> conflicts = new ArrayList<>();
}
