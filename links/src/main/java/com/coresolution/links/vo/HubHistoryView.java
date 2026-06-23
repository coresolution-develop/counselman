package com.coresolution.links.vo;

import lombok.Data;

/** 최근 사용 이력 표시용 (스냅샷 기반). */
@Data
public class HubHistoryView {
    private String linkType;     // PUBLIC / CUSTOM
    private Long linkId;
    private Long customLinkId;
    private String title;        // title_snapshot
    private String url;          // url_snapshot
    private String accessedAt;
}
