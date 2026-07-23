package com.coresolution.csm.vo;

import lombok.Data;

/** 관리자 공지 배너 (csm.hub_notice, 단일 행). */
@Data
public class HubNotice {
    private String message;
    private String level;      // info / warn
    private String activeYn;   // Y / N
    private String updatedAt;
}
