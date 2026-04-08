package com.coresolution.csm.vo;

import java.util.Date;

import lombok.Data;

@Data
public class CounselLog {

    private int idx;
    private int cs_idx;
    private String name;
    private String counsel_content;
    private String counsel_method;
    private String counsel_result;
    private String counsel_name;
    private Date created_at;
    private Date updated_at;
    private String counsel_at;
    private String inst;
}
