package com.coresolution.cancertreatment.model;

import java.io.Serializable;

public class SessionUser implements Serializable {

    private final String instCode;
    private final String username;

    public SessionUser(String instCode, String username) {
        this.instCode = instCode;
        this.username = username;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getUsername() {
        return username;
    }
}
