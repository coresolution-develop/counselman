package com.coresolution.cancertreatment.service;

import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.Patient;
import com.coresolution.cancertreatment.model.PatientRequest;
import com.coresolution.cancertreatment.repository.PatientRepository;

@Service
public class PatientService {

    private static final Set<String> ALLOWED_DISCOUNT_TYPES = Set.of("NONE", "PERCENT", "AMOUNT");

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<Patient> listPatients(String instCode, String keyword, String ward) {
        return patientRepository.findPatients(normalizeRequired(instCode, "병원 코드가 없습니다."), keyword, ward);
    }

    public Patient createPatient(String instCode, PatientRequest request) {
        ValidPatient valid = validate(instCode, request);
        return patientRepository.createPatient(
                valid.instCode(), valid.name(), valid.chartNo(), valid.room(), valid.ward(),
                valid.admissionDate(), valid.dischargeDate(), valid.treatmentInfo(), valid.note(),
                valid.prescriptionWeeks(), valid.copaymentRate(),
                valid.totalDiscountType(), valid.totalDiscountValue(),
                valid.prescriptionItemIds());
    }

    public Patient updatePatient(String instCode, Long id, PatientRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("환자 ID가 없습니다.");
        }
        ValidPatient valid = validate(instCode, request);
        return patientRepository.updatePatient(
                valid.instCode(), id, valid.name(), valid.chartNo(), valid.room(), valid.ward(),
                valid.admissionDate(), valid.dischargeDate(), valid.treatmentInfo(), valid.note(),
                valid.prescriptionWeeks(), valid.copaymentRate(),
                valid.totalDiscountType(), valid.totalDiscountValue(),
                valid.prescriptionItemIds());
    }

    private ValidPatient validate(String instCode, PatientRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("환자 정보를 입력해주세요.");
        }
        String normalizedInst = normalizeRequired(instCode, "병원 코드가 없습니다.");
        String name = normalizeRequired(request.getName(), "환자명을 입력해주세요.");
        if (name.length() > 100) {
            throw new IllegalArgumentException("환자명은 100자 이하로 입력해주세요.");
        }
        String chartNo = normalizeMax(request.getChartNo(), 50, "차트번호");
        String room = normalizeMax(request.getRoom(), 50, "병실/외래");
        String ward = normalizeMax(request.getWard(), 50, "병동");
        LocalDate admissionDate = parseDate(request.getAdmissionDate(), "입원일");
        LocalDate dischargeDate = parseDate(request.getDischargeDate(), "퇴원일");
        if (admissionDate != null && dischargeDate != null && dischargeDate.isBefore(admissionDate)) {
            throw new IllegalArgumentException("퇴원일은 입원일보다 빠를 수 없습니다.");
        }
        int weeks = request.getPrescriptionWeeks() == null ? 0 : request.getPrescriptionWeeks();
        if (weeks < 0 || weeks > 520) {
            throw new IllegalArgumentException("처방 주수는 0~520 사이여야 합니다.");
        }
        int copayment = request.getCopaymentRate() == null ? 100 : request.getCopaymentRate();
        if (copayment < 0 || copayment > 100) {
            throw new IllegalArgumentException("본인부담율은 0~100 사이여야 합니다.");
        }
        String discountType = request.getTotalDiscountType() == null ? "NONE" : request.getTotalDiscountType().trim().toUpperCase();
        if (!ALLOWED_DISCOUNT_TYPES.contains(discountType)) {
            throw new IllegalArgumentException("할인 종류는 NONE/PERCENT/AMOUNT 중 하나여야 합니다.");
        }
        int discountValue = request.getTotalDiscountValue() == null ? 0 : request.getTotalDiscountValue();
        if (discountValue < 0) {
            throw new IllegalArgumentException("할인 값은 0 이상이어야 합니다.");
        }
        if ("PERCENT".equals(discountType) && discountValue > 100) {
            throw new IllegalArgumentException("비율 할인은 100%를 초과할 수 없습니다.");
        }
        return new ValidPatient(
                normalizedInst, name, chartNo, room, ward,
                admissionDate, dischargeDate,
                normalize(request.getTreatmentInfo()), normalize(request.getNote()),
                weeks, copayment, discountType, discountValue,
                request.getPrescriptionItemIds() == null ? List.of() : List.copyOf(request.getPrescriptionItemIds()));
    }

    private record ValidPatient(
            String instCode, String name, String chartNo, String room, String ward,
            LocalDate admissionDate, LocalDate dischargeDate,
            String treatmentInfo, String note,
            int prescriptionWeeks, int copaymentRate,
            String totalDiscountType, int totalDiscountValue,
            List<Long> prescriptionItemIds) {
    }

    public Patient updateTextField(String instCode, Long id, String field, String value) {
        if (id == null) {
            throw new IllegalArgumentException("환자 ID가 없습니다.");
        }
        String normalizedInst = normalizeRequired(instCode, "병원 코드가 없습니다.");
        String normalizedField = normalizeRequired(field, "수정 항목이 없습니다.");
        String normalizedValue = normalize(value);
        validateField(normalizedField, normalizedValue);
        return patientRepository.updateTextField(normalizedInst, id, normalizedField, normalizedValue);
    }

    private void validateField(String field, String value) {
        switch (field) {
            case "name" -> normalizeRequired(value, "환자명을 입력해주세요.");
            case "chartNo" -> normalizeMax(value, 50, "차트번호");
            case "room" -> normalizeMax(value, 50, "병실/외래");
            case "ward" -> normalizeMax(value, 50, "병동");
            case "admissionDate" -> parseDate(value, "입원일");
            case "dischargeDate" -> parseDate(value, "퇴원일");
            case "treatmentInfo", "note" -> {
                if (value.length() > 1000) {
                    throw new IllegalArgumentException("입력값은 1000자 이하로 입력해주세요.");
                }
            }
            default -> throw new IllegalArgumentException("수정할 수 없는 항목입니다.");
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeMax(String value, int maxLength, String label) {
        String normalized = normalize(value);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "은 " + maxLength + "자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private LocalDate parseDate(String value, String label) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " 형식이 올바르지 않습니다.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
