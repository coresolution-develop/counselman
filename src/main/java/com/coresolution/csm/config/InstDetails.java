package com.coresolution.csm.config;

import java.io.Serializable;

import jakarta.servlet.http.HttpServletRequest;

public record InstDetails(String raw) implements Serializable {

    public static final String PARAM = "us_col_04";

    /** 화면에서 들어온 기관코드를 공백제거(대소문자 유지) */
    public String normalized() {
        return raw == null ? null : raw.trim();
    }

    /** HttpServletRequest에서 편하게 뽑아 쓰기 위한 팩토리 */
    public static InstDetails from(HttpServletRequest req) {
        return new InstDetails(req.getParameter(PARAM));
    }
}
