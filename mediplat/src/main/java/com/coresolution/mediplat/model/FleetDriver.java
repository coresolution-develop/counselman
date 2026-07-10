package com.coresolution.mediplat.model;

import java.time.LocalDateTime;

/**
 * 차량운행관리 운전자 로스터.
 *
 * <p>운전자 신원은 fleet 자체 로스터로 관리한다. 플랫폼 계정과의 연결은 {@code username}
 * (즉 {@code (inst_code, username)} 자연키)로 느슨하게 매핑한다. CounselMan SSO 사용자는
 * {@code mp_user.id}가 없을 수 있어 자연키를 1차 참조로 쓰고, 향후 canonical 신원 확정 시
 * {@code externalUserRef}에 매핑값(예: {@code mp_user.id})을 채운다.
 */
public class FleetDriver {
    private final Long id;
    private final String instCode;
    private final String name;
    private final String username;
    private final String employeeNumber;
    private final String department;
    private final String phone;
    private final String externalUserRef;
    private final String useYn;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public FleetDriver(
            Long id,
            String instCode,
            String name,
            String username,
            String employeeNumber,
            String department,
            String phone,
            String externalUserRef,
            String useYn,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.instCode = instCode;
        this.name = name;
        this.username = username;
        this.employeeNumber = employeeNumber;
        this.department = department;
        this.phone = phone;
        this.externalUserRef = externalUserRef;
        this.useYn = useYn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getName() {
        return name;
    }

    /** 플랫폼 계정 자연키((inst_code, username))의 username. 수기 등록 운전자는 null. */
    public String getUsername() {
        return username;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getDepartment() {
        return department;
    }

    public String getPhone() {
        return phone;
    }

    /** 향후 canonical 신원 매핑용(예: mp_user.id). P0에서는 비워 둔다. */
    public String getExternalUserRef() {
        return externalUserRef;
    }

    public String getUseYn() {
        return useYn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEnabled() {
        return "Y".equalsIgnoreCase(useYn);
    }
}
