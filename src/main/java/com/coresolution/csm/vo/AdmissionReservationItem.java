package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class AdmissionReservationItem {
    private long csIdx;
    private String patientName;   // cs_col_01 (복호화)
    private String gender;        // cs_col_02
    private String birthDate;     // cs_col_03
    private String guardianName;  // cs_col_13
    private String guardianPhone; // cs_col_15
    private String counselDate;   // cs_col_16 (상담일)
    private String counselor;     // cs_col_17 (담당상담사)
    private String plannedDate;   // cs_col_21 (입원예정일)
    private String roomName;      // cs_col_38 (병실호수)
    private String status;        // cs_col_19 (상담결과)
}
