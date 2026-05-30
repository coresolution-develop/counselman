package com.coresolution.sms.model;

import java.io.Serializable;

public class SessionUser implements Serializable {

    public static final String ROLE_VIEWER = "VIEWER";
    public static final String ROLE_MEMBER = "MEMBER";

    private final String instCode;
    private final String username;
    private final String role;

    public SessionUser(String instCode, String username) {
        this(instCode, username, ROLE_VIEWER);
    }

    public SessionUser(String instCode, String username, String role) {
        this.instCode = instCode;
        this.username = username;
        this.role = ROLE_MEMBER.equalsIgnoreCase(role) ? ROLE_MEMBER : ROLE_VIEWER;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean isMember() {
        return ROLE_MEMBER.equals(role);
    }
}
