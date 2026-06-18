package com.coresolution.cancertreatment.model;

public class ScheduleRecurrenceRequest {

    private Long patientId;
    private String patientName;
    private String ward;
    private Long treatmentRoomId;
    private Long seatId;
    private String treatmentName;
    private String treatmentOption;
    private String treatmentInfo;
    private String note;
    private String status;
    private String startDate;
    private String startTime;
    private boolean repeat;
    private Integer weekdayMask;
    private Integer occurrenceCount;
    private Integer prescriptionWeeks;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }

    public Long getTreatmentRoomId() { return treatmentRoomId; }
    public void setTreatmentRoomId(Long treatmentRoomId) { this.treatmentRoomId = treatmentRoomId; }

    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }

    public String getTreatmentName() { return treatmentName; }
    public void setTreatmentName(String treatmentName) { this.treatmentName = treatmentName; }

    public String getTreatmentOption() { return treatmentOption; }
    public void setTreatmentOption(String treatmentOption) { this.treatmentOption = treatmentOption; }

    public String getTreatmentInfo() { return treatmentInfo; }
    public void setTreatmentInfo(String treatmentInfo) { this.treatmentInfo = treatmentInfo; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    public Integer getWeekdayMask() { return weekdayMask; }
    public void setWeekdayMask(Integer weekdayMask) { this.weekdayMask = weekdayMask; }

    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public Integer getPrescriptionWeeks() { return prescriptionWeeks; }
    public void setPrescriptionWeeks(Integer prescriptionWeeks) { this.prescriptionWeeks = prescriptionWeeks; }
}
