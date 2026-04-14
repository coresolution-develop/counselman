package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class CounselReservation {
    private Long id;
    private String patient_name;
    private String patient_phone;
    private String guardian_name;
    private String call_summary;
    private Integer priority;
    private String reserved_at;
    private String status;
    private Integer linked_cs_idx;
    private String created_by;
    private String completed_by;
    private String completed_at;
    private String created_at;
    private String updated_at;
}
