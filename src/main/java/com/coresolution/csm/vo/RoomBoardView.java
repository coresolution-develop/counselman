package com.coresolution.csm.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class RoomBoardView {
    private String snapshotDate;
    private String snapshotTime;
    private Long snapshotId;
    private Integer totalLicensedBeds;
    private Integer totalOccupiedBeds;
    private Integer totalAvailableBeds;
    private Double totalOccupancyRate;
    private List<RoomBoardWardView> wards = new ArrayList<>();
}
