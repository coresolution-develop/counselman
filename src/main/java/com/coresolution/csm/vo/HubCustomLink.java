package com.coresolution.csm.vo;

import lombok.Data;

/** 회원 본인만 보는 개인 커스텀 링크 (csm.hub_member_custom_link). */
@Data
public class HubCustomLink {
    private Long id;
    private Long memberId;
    private String title;
    private String url;
    private String memo;
    private String category;
    private Integer sortOrder;
    private String createdAt;
}
