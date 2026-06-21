package com.coresolution.csm.vo;

import lombok.Data;

/**
 * 전사 링크 허브 개인화 계층의 독립 회원 계정 (csm.hub_member).
 * csm 기존 직원/기관 로그인(Userdata)과는 별개의 신원이다.
 */
@Data
public class HubMember {
    private Long id;
    private String email;
    private String password;   // BCrypt 해시
    private String name;
    private String role;       // USER / ADMIN
    private String status;     // ACTIVE / DISABLED
    private String createdAt;
    private String lastLoginAt;
}
