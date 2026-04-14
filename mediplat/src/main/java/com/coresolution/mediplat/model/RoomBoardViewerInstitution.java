package com.coresolution.mediplat.model;

public class RoomBoardViewerInstitution {
    private final String instCode;
    private final String instName;
    private final String launchUrl;

    public RoomBoardViewerInstitution(String instCode, String instName, String launchUrl) {
        this.instCode = instCode;
        this.instName = instName;
        this.launchUrl = launchUrl;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getInstName() {
        return instName;
    }

    public String getLaunchUrl() {
        return launchUrl;
    }
}
