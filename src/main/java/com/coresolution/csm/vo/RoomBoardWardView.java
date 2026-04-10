package com.coresolution.csm.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class RoomBoardWardView {
    private String wardName;
    private Integer licensedBeds;
    private Integer occupiedBeds;
    private Integer availableBeds;
    private Double occupancyRate;
    private List<RoomBoardRoomView> rooms = new ArrayList<>();
}
