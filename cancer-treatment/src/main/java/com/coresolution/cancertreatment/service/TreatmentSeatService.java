package com.coresolution.cancertreatment.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.TreatmentSeat;
import com.coresolution.cancertreatment.model.TreatmentSeatRequest;
import com.coresolution.cancertreatment.repository.TreatmentSeatRepository;

@Service
public class TreatmentSeatService {

    private final TreatmentSeatRepository treatmentSeatRepository;

    public TreatmentSeatService(TreatmentSeatRepository treatmentSeatRepository) {
        this.treatmentSeatRepository = treatmentSeatRepository;
    }

    public List<TreatmentSeat> listSeats(String instCode, Long treatmentRoomId) {
        return treatmentSeatRepository.findSeats(requireText(instCode, "병원 코드가 없습니다."), treatmentRoomId);
    }

    @Transactional
    public TreatmentSeat createSeat(String instCode, TreatmentSeatRequest request) {
        ValidSeat valid = validate(request);
        try {
            return treatmentSeatRepository.createSeat(
                    requireText(instCode, "병원 코드가 없습니다."),
                    valid.treatmentRoomId(),
                    valid.seatCode(),
                    valid.seatName(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 자리 코드입니다.");
        }
    }

    @Transactional
    public TreatmentSeat updateSeat(String instCode, Long id, TreatmentSeatRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("자리 ID가 없습니다.");
        }
        ValidSeat valid = validate(request);
        try {
            return treatmentSeatRepository.updateSeat(
                    requireText(instCode, "병원 코드가 없습니다."),
                    id,
                    valid.treatmentRoomId(),
                    valid.seatCode(),
                    valid.seatName(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 자리 코드입니다.");
        }
    }

    @Transactional
    public void deleteSeat(String instCode, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("자리 ID가 없습니다.");
        }
        treatmentSeatRepository.deactivateSeat(requireText(instCode, "병원 코드가 없습니다."), id);
    }

    private ValidSeat validate(TreatmentSeatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("자리 정보를 입력해주세요.");
        }
        if (request.getTreatmentRoomId() == null) {
            throw new IllegalArgumentException("치료실을 선택해주세요.");
        }
        String seatCode = requireMax(request.getSeatCode(), 50, "자리 코드");
        String seatName = requireMax(request.getSeatName(), 100, "자리명");
        int displayOrder = request.getDisplayOrder() == null ? 0 : request.getDisplayOrder();
        return new ValidSeat(request.getTreatmentRoomId(), seatCode, seatName, displayOrder);
    }

    private String requireMax(String value, int maxLength, String label) {
        String normalized = requireText(value, label + "을 입력해주세요.");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "은 " + maxLength + "자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private record ValidSeat(Long treatmentRoomId, String seatCode, String seatName, int displayOrder) {
    }
}
