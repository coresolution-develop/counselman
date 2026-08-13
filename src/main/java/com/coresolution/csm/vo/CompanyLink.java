package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class CompanyLink {
    private Long id;
    private String title;
    private String url;
    private String description;
    private String category;
    /** prod | dev | demo. 비어 있으면 이름·host로 자동 판정한다. */
    private String env;
    private Integer sortOrder;
    private String useYn;
    private String createdAt;
    private String updatedAt;
}
