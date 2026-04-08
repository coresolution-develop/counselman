package com.coresolution.csm.vo;

import java.util.Date;

import lombok.Data;

@Data
public class Card {

    private int id;
    private String title;
    private String content;
    private Date created_at;
    private String inst;
}
