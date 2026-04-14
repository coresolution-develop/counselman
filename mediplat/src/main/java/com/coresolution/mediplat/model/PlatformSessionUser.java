package com.coresolution.mediplat.model;

import java.io.Serializable;

public class PlatformSessionUser implements Serializable {
    private final String instCode;
    private final String username;
    private final String displayName;
    private final String roleCode;

    public PlatformSessionUser(String instCode, String username, String displayName, String roleCode) {
        this.instCode = instCode;
        this.username = username;
        this.displayName = displayName;
        this.roleCode = roleCode;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equalsIgnoreCase(roleCode);
    }

    public boolean isRoomBoardViewer() {
        return "ROOM_BOARD_VIEWER".equalsIgnoreCase(roleCode);
    }
}
