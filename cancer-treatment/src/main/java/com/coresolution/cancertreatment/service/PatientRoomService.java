package com.coresolution.cancertreatment.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.PatientRoom;
import com.coresolution.cancertreatment.model.PatientRoomRequest;
import com.coresolution.cancertreatment.repository.PatientRoomRepository;

@Service
public class PatientRoomService {

    private final PatientRoomRepository patientRoomRepository;

    public PatientRoomService(PatientRoomRepository patientRoomRepository) {
        this.patientRoomRepository = patientRoomRepository;
    }

    public List<PatientRoom> listRooms(String instCode) {
        return patientRoomRepository.findRooms(requireText(instCode, "병원 코드가 없습니다."));
    }

    @Transactional
    public PatientRoom createRoom(String instCode, PatientRoomRequest request) {
        ValidRoom valid = validate(request);
        try {
            return patientRoomRepository.createRoom(
                    requireText(instCode, "병원 코드가 없습니다."),
                    valid.roomCode(),
                    valid.roomName(),
                    valid.wardId(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 병실 코드입니다.");
        }
    }

    @Transactional
    public PatientRoom updateRoom(String instCode, Long id, PatientRoomRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("병실 ID가 없습니다.");
        }
        ValidRoom valid = validate(request);
        try {
            return patientRoomRepository.updateRoom(
                    requireText(instCode, "병원 코드가 없습니다."),
                    id,
                    valid.roomCode(),
                    valid.roomName(),
                    valid.wardId(),
                    valid.displayOrder());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("이미 등록된 병실 코드입니다.");
        }
    }

    @Transactional
    public void deleteRoom(String instCode, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("병실 ID가 없습니다.");
        }
        patientRoomRepository.deactivateRoom(requireText(instCode, "병원 코드가 없습니다."), id);
    }

    private ValidRoom validate(PatientRoomRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("병실 정보를 입력해주세요.");
        }
        String roomCode = requireMax(request.getRoomCode(), 50, "병실 코드");
        String roomName = requireMax(request.getRoomName(), 100, "병실명");
        int displayOrder = request.getDisplayOrder() == null ? 0 : request.getDisplayOrder();
        return new ValidRoom(roomCode, roomName, request.getWardId(), displayOrder);
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

    private record ValidRoom(String roomCode, String roomName, Long wardId, int displayOrder) {
    }
}
