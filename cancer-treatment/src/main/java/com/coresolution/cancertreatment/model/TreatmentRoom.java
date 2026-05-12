package com.coresolution.cancertreatment.model;

import java.util.List;

public class TreatmentRoom {

    private final Long id;
    private final String managementNo;
    private final String roomName;
    private final List<String> treatmentItems;
    private final String managerName;
    private final String note;
    private final Integer displayOrder;

    public TreatmentRoom(
            Long id,
            String managementNo,
            String roomName,
            List<String> treatmentItems,
            String managerName,
            String note,
            Integer displayOrder) {
        this.id = id;
        this.managementNo = managementNo;
        this.roomName = roomName;
        this.treatmentItems = treatmentItems == null ? List.of() : List.copyOf(treatmentItems);
        this.managerName = managerName;
        this.note = note;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getManagementNo() {
        return managementNo;
    }

    public String getRoomName() {
        return roomName;
    }

    public List<String> getTreatmentItems() {
        return treatmentItems;
    }

    public String getTreatmentItem() {
        return treatmentItems.isEmpty() ? "" : treatmentItems.get(0);
    }

    public String getManagerName() {
        return managerName;
    }

    public String getNote() {
        return note;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
