package com.coresolution.csm.vo;

import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * HttpSession에 저장되는 경량 허브 회원 식별자.
 * spring-session-jdbc로 MySQL에 직렬화 저장되므로 Serializable + serialVersionUID 고정.
 * 비밀번호 해시 등 민감값은 담지 않는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HubMemberSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String email;
    private String name;
    private String role;   // USER / ADMIN

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
