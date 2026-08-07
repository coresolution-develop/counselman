package com.coresolution.mediplat.model;

import java.io.Serializable;

/**
 * 기기 기억으로 복원된 운전자 신원의 경량 세션 표현. 관리자 로그인({@code PlatformSessionUser},
 * 세션 키 {@code mediplatUser})과는 완전히 별개다. 세션 클러스터링 대비 {@link Serializable}.
 */
public class FleetDriverPrincipal implements Serializable {
    private final Long driverId;
    private final String instCode;
    private final String name;
    private final String username;

    public FleetDriverPrincipal(Long driverId, String instCode, String name, String username) {
        this.driverId = driverId;
        this.instCode = instCode;
        this.name = name;
        this.username = username;
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }
}
