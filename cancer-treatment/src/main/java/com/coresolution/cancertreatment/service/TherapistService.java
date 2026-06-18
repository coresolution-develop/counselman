package com.coresolution.cancertreatment.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.Therapist;
import com.coresolution.cancertreatment.model.TherapistRequest;
import com.coresolution.cancertreatment.repository.TherapistRepository;

@Service
public class TherapistService {

    private final TherapistRepository therapistRepository;

    public TherapistService(TherapistRepository therapistRepository) {
        this.therapistRepository = therapistRepository;
    }

    public List<Therapist> listTherapists(String instCode) {
        return therapistRepository.findTherapists(requireText(instCode, "병원 코드가 없습니다."));
    }

    @Transactional
    public Therapist createTherapist(String instCode, TherapistRequest request) {
        ValidTherapist valid = validate(request);
        try {
            return therapistRepository.createTherapist(
                    requireText(instCode, "병원 코드가 없습니다."), valid.name(), valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 치료사입니다.");
        }
    }

    @Transactional
    public Therapist updateTherapist(String instCode, Long id, TherapistRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("치료사 ID가 없습니다.");
        }
        ValidTherapist valid = validate(request);
        try {
            return therapistRepository.updateTherapist(
                    requireText(instCode, "병원 코드가 없습니다."), id, valid.name(), valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 치료사입니다.");
        }
    }

    @Transactional
    public void deleteTherapist(String instCode, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("치료사 ID가 없습니다.");
        }
        therapistRepository.deactivateTherapist(requireText(instCode, "병원 코드가 없습니다."), id);
    }

    private ValidTherapist validate(TherapistRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("치료사 정보를 입력해주세요.");
        }
        String name = requireText(request.getName(), "치료사명을 입력해주세요.");
        if (name.length() > 100) {
            throw new IllegalArgumentException("치료사명은 100자 이하로 입력해주세요.");
        }
        int displayOrder = request.getDisplayOrder() == null ? 0 : request.getDisplayOrder();
        return new ValidTherapist(name, displayOrder);
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private record ValidTherapist(String name, int displayOrder) {
    }
}
