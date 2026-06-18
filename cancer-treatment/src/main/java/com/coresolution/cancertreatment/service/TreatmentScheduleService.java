package com.coresolution.cancertreatment.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.coresolution.cancertreatment.model.TreatmentSchedule;
import com.coresolution.cancertreatment.model.TreatmentScheduleRequest;
import com.coresolution.cancertreatment.repository.TreatmentScheduleRepository;

@Service
public class TreatmentScheduleService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final List<String> ALLOWED_STATUSES = List.of("예약", "치료완료", "예약취소");

    private final TreatmentScheduleRepository scheduleRepository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public TreatmentScheduleService(TreatmentScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public List<TreatmentSchedule> listSchedules(
            String instCode, String date, String keyword, String treatmentName, String status) {
        return listSchedules(instCode, date, keyword, treatmentName, status, null);
    }

    public List<TreatmentSchedule> listSchedules(
            String instCode, String date, String keyword, String treatmentName, String status, Long roomId) {
        return scheduleRepository.findSchedules(
                requireInst(instCode), normalize(date), normalize(keyword),
                normalize(treatmentName), normalize(status), roomId);
    }

    public List<TreatmentSchedule> listSchedulesByRange(String instCode, String from, String to) {
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
            throw new IllegalArgumentException("조회 기간(from/to)이 필요합니다.");
        }
        return scheduleRepository.findSchedulesByRange(requireInst(instCode), from, to);
    }

    public Map<String, Long> getSummary(String instCode, String date) {
        String targetDate = StringUtils.hasText(date) ? date.trim() : LocalDate.now().toString();
        List<TreatmentSchedule> targetSchedules = listSchedules(instCode, targetDate, null, null, null);
        long reserved = targetSchedules.stream().filter(schedule -> "예약".equals(schedule.getStatus())).count();
        long completed = targetSchedules.stream().filter(schedule -> "치료완료".equals(schedule.getStatus())).count();
        long canceled = targetSchedules.stream().filter(schedule -> "예약취소".equals(schedule.getStatus())).count();
        return Map.of(
                "total", (long) targetSchedules.size(),
                "reserved", reserved,
                "completed", completed,
                "canceled", canceled);
    }

    public Map<String, Object> getDashboard(String instCode, String date) {
        String targetDate = StringUtils.hasText(date) ? date.trim() : LocalDate.now().toString();
        List<TreatmentSchedule> targetSchedules = listSchedules(instCode, targetDate, null, null, null);
        return Map.of(
                "summary", getSummary(instCode, targetDate),
                "byTreatment", countBy(targetSchedules, TreatmentSchedule::getTreatmentName),
                "byWard", countBy(targetSchedules, TreatmentSchedule::getWard),
                "recent", targetSchedules.stream()
                        .sorted(Comparator.comparing(TreatmentSchedule::getStartTime))
                        .limit(6)
                        .toList());
    }

    public TreatmentSchedule updateStatus(String instCode, Long id, String status) {
        String normalizedStatus = normalize(status);
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("지원하지 않는 치료상태입니다.");
        }
        TreatmentSchedule schedule = scheduleRepository.updateStatus(requireInst(instCode), id, normalizedStatus);
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public TreatmentSchedule updateStartTime(String instCode, Long id, String startTime) {
        String normalized = normalizeStartTime(startTime);
        TreatmentSchedule schedule = scheduleRepository.updateStartTime(requireInst(instCode), id, normalized);
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public TreatmentSchedule updateTextField(String instCode, Long id, String field, String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.length() > 1000) {
            throw new IllegalArgumentException("입력값은 1000자 이하로 입력해주세요.");
        }
        String column = switch (field == null ? "" : field) {
            case "treatmentInfo" -> "treatment_info";
            case "note" -> "note";
            default -> throw new IllegalArgumentException("수정할 수 없는 항목입니다.");
        };
        TreatmentSchedule schedule = scheduleRepository.updateTextField(requireInst(instCode), id, column, normalizedValue);
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public TreatmentSchedule createSchedule(String instCode, TreatmentScheduleRequest req) {
        validateRequest(req);
        TreatmentSchedule schedule = scheduleRepository.create(
                requireInst(instCode),
                req.getPatientId(),
                req.getTreatmentDate().trim(),
                req.getStartTime().trim(),
                req.getPatientName().trim(),
                normalize(req.getWard()),
                req.getTreatmentName().trim(),
                normalize(req.getTreatmentOption()),
                StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "예약",
                normalize(req.getTreatmentInfo()),
                normalize(req.getNote()));
        sendScheduleChanged("CREATED", schedule);
        return schedule;
    }

    public TreatmentSchedule updateSchedule(String instCode, Long id, TreatmentScheduleRequest req) {
        validateRequest(req);
        TreatmentSchedule schedule = scheduleRepository.update(
                requireInst(instCode),
                id,
                req.getPatientId(),
                req.getTreatmentDate().trim(),
                req.getStartTime().trim(),
                req.getPatientName().trim(),
                normalize(req.getWard()),
                req.getTreatmentName().trim(),
                normalize(req.getTreatmentOption()),
                StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "예약",
                normalize(req.getTreatmentInfo()),
                normalize(req.getNote()));
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public void deleteSchedule(String instCode, Long id) {
        String inst = requireInst(instCode);
        TreatmentSchedule captured = scheduleRepository.findById(inst, id)
                .orElseThrow(() -> new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다."));
        scheduleRepository.delete(inst, id);
        sendScheduleChanged("DELETED", captured);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        sendEvent(emitter, "connected", Map.of("message", "connected"));
        return emitter;
    }

    private void validateRequest(TreatmentScheduleRequest req) {
        if (req == null || !StringUtils.hasText(req.getTreatmentDate()) || !StringUtils.hasText(req.getStartTime())
                || !StringUtils.hasText(req.getPatientName()) || !StringUtils.hasText(req.getTreatmentName())) {
            throw new IllegalArgumentException("필수 항목을 입력해주세요.");
        }
        if (StringUtils.hasText(req.getStatus()) && !ALLOWED_STATUSES.contains(req.getStatus().trim())) {
            throw new IllegalArgumentException("지원하지 않는 치료상태입니다.");
        }
        if (req.getPatientName().trim().length() > 100) {
            throw new IllegalArgumentException("환자명은 100자 이하로 입력해주세요.");
        }
        if (req.getTreatmentName().trim().length() > 100) {
            throw new IllegalArgumentException("치료종류는 100자 이하로 입력해주세요.");
        }
        validateDate(req.getTreatmentDate().trim());
        normalizeStartTime(req.getStartTime());
    }

    private void validateDate(String date) {
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("치료일 형식이 올바르지 않습니다.");
        }
    }

    private String normalizeStartTime(String startTime) {
        String normalized = normalize(startTime);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("시작시간을 입력해주세요.");
        }
        if (normalized.length() == 4 && normalized.charAt(1) == ':') {
            normalized = "0" + normalized;
        }
        try {
            LocalTime parsed = LocalTime.parse(normalized);
            return String.format("%02d:%02d", parsed.getHour(), parsed.getMinute());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시작시간은 HH:mm 형식이어야 합니다.");
        }
    }

    private void sendScheduleChanged(String type, TreatmentSchedule schedule) {
        Map<String, Object> payload = Map.of(
                "type", type,
                "scheduleId", schedule.getId(),
                "treatmentDate", schedule.getTreatmentDate(),
                "status", schedule.getStatus());
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            sendEvent(emitter, "schedule-changed", payload);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.complete();
        }
    }

    private String requireInst(String instCode) {
        if (!StringUtils.hasText(instCode)) {
            throw new IllegalArgumentException("병원 코드가 없습니다.");
        }
        return instCode.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Long> countBy(
            List<TreatmentSchedule> targetSchedules,
            java.util.function.Function<TreatmentSchedule, String> classifier) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (TreatmentSchedule schedule : targetSchedules) {
            String key = normalize(classifier.apply(schedule));
            if (!StringUtils.hasText(key)) {
                key = "미지정";
            }
            result.put(key, result.getOrDefault(key, 0L) + 1);
        }
        return result;
    }
}
