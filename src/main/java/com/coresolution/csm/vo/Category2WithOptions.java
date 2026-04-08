package com.coresolution.csm.vo;

import java.util.List;

import lombok.Data;

@Data
public class Category2WithOptions {
    private Category2 category2;
    private List<Category3> options;
}
