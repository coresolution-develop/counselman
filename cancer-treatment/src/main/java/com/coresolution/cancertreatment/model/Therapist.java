package com.coresolution.cancertreatment.model;

public class Therapist {

    private final Long id;
    private final String name;
    private final Integer displayOrder;

    public Therapist(Long id, String name, Integer displayOrder) {
        this.id = id;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
