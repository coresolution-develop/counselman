package com.coresolution.cancertreatment.model;

public class SettingItem {

    private final Long id;
    private final String code;
    private final String name;
    private final String detail;
    private final String color;
    private final Integer displayOrder;

    public SettingItem(Long id, String code, String name, String detail, Integer displayOrder) {
        this(id, code, name, detail, null, displayOrder);
    }

    public SettingItem(Long id, String code, String name, String detail, String color, Integer displayOrder) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.detail = detail;
        this.color = color;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDetail() {
        return detail;
    }

    public String getColor() {
        return color;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
