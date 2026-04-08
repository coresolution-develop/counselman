package com.coresolution.csm.vo;

import java.util.Date;

import lombok.Data;

@Data
public class SmsTemplate {

    private int id;
    private String title;
    private String template;
    private Date created_at;
    private String inst;
}
