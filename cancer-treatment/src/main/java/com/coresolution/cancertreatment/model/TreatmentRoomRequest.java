package com.coresolution.cancertreatment.model;

import java.util.List;

public class TreatmentRoomRequest {

    private String managementNo;
    private String roomName;
    private String treatmentItem;
    private List<String> treatmentItems;
    private String managerName;
    private String note;
    private Integer displayOrder;

    public String getManagementNo() {
        return managementNo;
    }

    public void setManagementNo(String managementNo) {
        this.managementNo = managementNo;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getTreatmentItem() {
        return treatmentItem;
    }

    public void setTreatmentItem(String treatmentItem) {
        this.treatmentItem = treatmentItem;
    }

    public List<String> getTreatmentItems() {
        return treatmentItems;
    }

    public void setTreatmentItems(List<String> treatmentItems) {
        this.treatmentItems = treatmentItems;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
