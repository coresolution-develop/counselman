package com.coresolution.cancertreatment.service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.SettingItem;
import com.coresolution.cancertreatment.model.SettingItemRequest;
import com.coresolution.cancertreatment.repository.SettingRepository;
import com.coresolution.cancertreatment.repository.SettingRepository.SettingCategory;

@Service
public class SettingService {

    private final SettingRepository settingRepository;

    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public Map<String, List<SettingItem>> listAll(String instCode) {
        String normalizedInst = requireText(instCode, "병원 코드가 없습니다.");
        return Map.of(
                "treatmentTypes", settingRepository.findItems(SettingCategory.TREATMENT_TYPES, normalizedInst),
                "treatmentOptions", settingRepository.findItems(SettingCategory.TREATMENT_OPTIONS, normalizedInst),
                "treatmentStatuses", settingRepository.findItems(SettingCategory.TREATMENT_STATUSES, normalizedInst),
                "timeSlots", settingRepository.findItems(SettingCategory.TIME_SLOTS, normalizedInst),
                "wards", settingRepository.findItems(SettingCategory.WARDS, normalizedInst),
                "packageCategories", settingRepository.findItems(SettingCategory.PACKAGE_CATEGORIES, normalizedInst));
    }

    public List<SettingItem> listItems(String instCode, String categoryKey) {
        return settingRepository.findItems(category(categoryKey), requireText(instCode, "병원 코드가 없습니다."));
    }

    public SettingItem createItem(String instCode, String categoryKey, SettingItemRequest request) {
        SettingCategory category = category(categoryKey);
        ValidSetting valid = validate(category, request);
        try {
            return settingRepository.createItem(
                    category,
                    requireText(instCode, "병원 코드가 없습니다."),
                    valid.code(),
                    valid.name(),
                    valid.detail(),
                    valid.color(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 설정입니다.");
        }
    }

    public SettingItem updateItem(String instCode, String categoryKey, Long id, SettingItemRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("설정 ID가 없습니다.");
        }
        SettingCategory category = category(categoryKey);
        ValidSetting valid = validate(category, request);
        try {
            return settingRepository.updateItem(
                    category,
                    requireText(instCode, "병원 코드가 없습니다."),
                    id,
                    valid.code(),
                    valid.name(),
                    valid.detail(),
                    valid.color(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 설정입니다.");
        }
    }

    public void deleteItem(String instCode, String categoryKey, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("설정 ID가 없습니다.");
        }
        settingRepository.deactivateItem(category(categoryKey), requireText(instCode, "병원 코드가 없습니다."), id);
    }

    private SettingCategory category(String categoryKey) {
        return SettingCategory.fromKey(categoryKey == null ? "" : categoryKey.trim());
    }

    private ValidSetting validate(SettingCategory category, SettingItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("설정 값을 입력해주세요.");
        }
        int displayOrder = request.getDisplayOrder() == null ? 0 : request.getDisplayOrder();
        return switch (category) {
            case TREATMENT_TYPES -> {
                String name = requireMax(request.getName(), 100, "치료 항목명");
                yield new ValidSetting(name, name, normalizeMax(request.getDetail(), 100, "치료실"), "", displayOrder);
            }
            case TREATMENT_OPTIONS -> {
                String code = requireMax(request.getCode(), 50, "옵션 코드");
                yield new ValidSetting(
                        code,
                        requireMax(request.getName(), 100, "옵션명"),
                        "",
                        normalizeColor(request.getColor()),
                        displayOrder);
            }
            case TREATMENT_STATUSES -> {
                String code = requireMax(request.getCode(), 30, "상태 코드");
                yield new ValidSetting(code, requireMax(request.getName(), 30, "상태명"), "", "", displayOrder);
            }
            case TIME_SLOTS -> {
                String time = requireText(request.getCode(), "시간을 입력해주세요.");
                validateTime(time);
                yield new ValidSetting(time, time, "", "", displayOrder);
            }
            case WARDS -> {
                String code = requireMax(request.getCode(), 50, "병동 코드");
                String name = requireMax(request.getName(), 100, "병동명");
                yield new ValidSetting(code, name, normalizeAdmissionType(request.getDetail()), "", displayOrder);
            }
            case PACKAGE_CATEGORIES -> {
                String name = requireMax(request.getName(), 100, "카테고리명");
                yield new ValidSetting(name, name, "", "", displayOrder);
            }
        };
    }

    private String normalizeAdmissionType(String value) {
        String normalized = normalize(value).toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            return "INPATIENT";
        }
        if (!normalized.equals("INPATIENT") && !normalized.equals("OUTPATIENT")) {
            throw new IllegalArgumentException("입원/외래 구분은 INPATIENT 또는 OUTPATIENT여야 합니다.");
        }
        return normalized;
    }

    private String normalizeColor(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return "#1a74bf";
        }
        if (!normalized.matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("색상은 #RRGGBB 형식으로 선택해주세요.");
        }
        return normalized;
    }

    private void validateTime(String value) {
        try {
            LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시간은 HH:mm 형식으로 입력해주세요.");
        }
    }

    private String requireMax(String value, int maxLength, String label) {
        String normalized = requireText(value, label + "을 입력해주세요.");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "은 " + maxLength + "자 이하로 입력해주세요.");
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

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidSetting(String code, String name, String detail, String color, int displayOrder) {
    }
}
