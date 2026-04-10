package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class RoomBoardPatient {
    private Long id;
    private Long snapshotId;
    private String wardName;
    private String roomName;
    private String patientNo;
    private String patientName;
    private String gender;
    private String age;
    private String admissionDate;
    private String doctorName;
    private String patientType;
    private String diseaseName;
    private String diseaseCode;
    private String phonePatient;
    private String phoneGuardian;
    private String memo;
    private String rawRow;
}
