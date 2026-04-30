package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class CompanyLink {
    private Long id;
    private String title;
    private String url;
    private String description;
    private String category;
    private Integer sortOrder;
    private String useYn;
    private String createdAt;
    private String updatedAt;
}
