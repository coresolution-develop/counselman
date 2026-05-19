package com.coresolution.mediplat.model;

import java.time.LocalDateTime;

public class PlatformLoginAudit {
    private final Long id;
    private final String instCode;
    private final String instName;
    private final String username;
    private final String displayName;
    private final String roleCode;
    private final LocalDateTime loginAt;
    private final LocalDateTime logoutAt;
    private final Long sessionSeconds;

    public PlatformLoginAudit(
            Long id,
            String instCode,
            String instName,
            String username,
            String displayName,
            String roleCode,
            LocalDateTime loginAt,
            LocalDateTime logoutAt,
            Long sessionSeconds) {
        this.id = id;
        this.instCode = instCode;
        this.instName = instName;
        this.username = username;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.loginAt = loginAt;
        this.logoutAt = logoutAt;
        this.sessionSeconds = sessionSeconds;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getInstName() {
        return instName;
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

    public LocalDateTime getLoginAt() {
        return loginAt;
    }

    public LocalDateTime getLogoutAt() {
        return logoutAt;
    }

    public Long getSessionSeconds() {
        return sessionSeconds;
    }
}
