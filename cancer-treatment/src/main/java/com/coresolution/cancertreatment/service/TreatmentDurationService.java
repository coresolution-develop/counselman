package com.coresolution.cancertreatment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.TreatmentTypeDuration;
import com.coresolution.cancertreatment.repository.TreatmentDurationRepository;

@Service
public class TreatmentDurationService {

    private static final int MAX_DURATION_MINUTES = 600; // 10시간 — 비정상 입력 방어

    private final TreatmentDurationRepository durationRepository;

    public TreatmentDurationService(TreatmentDurationRepository durationRepository) {
        this.durationRepository = durationRepository;
    }

    public List<TreatmentTypeDuration> listDurations(String instCode) {
        return durationRepository.findAll(requireText(instCode, "병원 코드가 없습니다."));
    }

    public void updateDuration(String instCode, Long id, Integer durationMinutes) {
        if (id == null) {
            throw new IllegalArgumentException("치료 항목 ID가 없습니다.");
        }
        Integer normalized = durationMinutes;
        if (normalized != null) {
            if (normalized < 0) {
                throw new IllegalArgumentException("소요시간은 0분 이상이어야 합니다.");
            }
            if (normalized > MAX_DURATION_MINUTES) {
                throw new IllegalArgumentException("소요시간은 " + MAX_DURATION_MINUTES + "분을 초과할 수 없습니다.");
            }
            if (normalized == 0) {
                normalized = null; // 0은 '미설정'으로 저장
            }
        }
        durationRepository.updateDuration(requireText(instCode, "병원 코드가 없습니다."), id, normalized);
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
