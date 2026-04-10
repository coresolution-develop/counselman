package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class RoomBoardImportRow {
    private String wardName;
    private String roomName;
    private String patientNo;
    private String patientName;
    private String gender;
    private String age;
    private String admissionDate;
    private String doctorName;
    private String patientType;
    private String memo;
}
