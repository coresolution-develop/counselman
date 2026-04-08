package com.coresolution.csm.vo;

import java.util.List;

import lombok.Data;

@Data
public class Category1WithSubcategoriesAndOptions {
    private Category1 category1;
    private List<Category2WithOptions> subcategories;

}
