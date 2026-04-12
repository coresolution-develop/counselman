package com.coresolution.csm.vo;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

@Data
public class ModuleFeatureRow {
    private String instCode;
    private String instName;
    private Map<String, Boolean> featureStates = new LinkedHashMap<>();
}
