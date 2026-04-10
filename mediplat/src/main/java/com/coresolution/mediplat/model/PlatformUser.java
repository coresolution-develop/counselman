package com.coresolution.mediplat.model;

public class PlatformUser {
    private final Long id;
    private final String instCode;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final String roleCode;
    private final String useYn;

    public PlatformUser(
            Long id,
            String instCode,
            String username,
            String passwordHash,
            String displayName,
            String roleCode,
            String useYn) {
        this.id = id;
        this.instCode = instCode;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.useYn = useYn;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getUseYn() {
        return useYn;
    }

    public boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equalsIgnoreCase(roleCode);
    }
}
