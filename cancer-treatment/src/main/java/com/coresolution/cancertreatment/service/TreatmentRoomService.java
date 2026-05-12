package com.coresolution.cancertreatment.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.TreatmentRoom;
import com.coresolution.cancertreatment.model.TreatmentRoomRequest;
import com.coresolution.cancertreatment.repository.TreatmentRoomRepository;

@Service
public class TreatmentRoomService {

    private final TreatmentRoomRepository treatmentRoomRepository;

    public TreatmentRoomService(TreatmentRoomRepository treatmentRoomRepository) {
        this.treatmentRoomRepository = treatmentRoomRepository;
    }

    public List<TreatmentRoom> listRooms(String instCode) {
        return treatmentRoomRepository.findRooms(requireText(instCode, "병원 코드가 없습니다."));
    }

    @Transactional
    public TreatmentRoom createRoom(String instCode, TreatmentRoomRequest request) {
        ValidRoom valid = validate(request);
        try {
            return treatmentRoomRepository.createRoom(
                    requireText(instCode, "병원 코드가 없습니다."),
                    valid.managementNo(),
                    valid.roomName(),
                    valid.treatmentItems(),
                    valid.managerName(),
                    valid.note(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 치료실 정보입니다.");
        }
    }

    @Transactional
    public TreatmentRoom updateRoom(String instCode, Long id, TreatmentRoomRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("치료실 ID가 없습니다.");
        }
        ValidRoom valid = validate(request);
        try {
            return treatmentRoomRepository.updateRoom(
                    requireText(instCode, "병원 코드가 없습니다."),
                    id,
                    valid.managementNo(),
                    valid.roomName(),
                    valid.treatmentItems(),
                    valid.managerName(),
                    valid.note(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 치료실 정보입니다.");
        }
    }

    @Transactional
    public void deleteRoom(String instCode, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("치료실 ID가 없습니다.");
        }
        treatmentRoomRepository.deactivateRoom(requireText(instCode, "병원 코드가 없습니다."), id);
    }

    private ValidRoom validate(TreatmentRoomRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("치료실 정보를 입력해주세요.");
        }
        return new ValidRoom(
                normalizeMax(request.getManagementNo(), 50, "관리번호"),
                requireMax(request.getRoomName(), 100, "치료실"),
                normalizeTreatmentItems(request),
                normalizeMax(request.getManagerName(), 100, "담당자"),
                normalize(request.getNote()),
                request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
    }

    private List<String> normalizeTreatmentItems(TreatmentRoomRequest request) {
        List<String> source = request.getTreatmentItems();
        if (source == null || source.isEmpty()) {
            source = request.getTreatmentItem() == null ? List.of() : List.of(request.getTreatmentItem());
        }
        List<String> items = source.stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        for (String item : items) {
            if (item.length() > 100) {
                throw new IllegalArgumentException("치료항목은 100자 이하로 입력해주세요.");
            }
        }
        return items;
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

    private record ValidRoom(
            String managementNo,
            String roomName,
            List<String> treatmentItems,
            String managerName,
            String note,
            int displayOrder) {
    }
}
