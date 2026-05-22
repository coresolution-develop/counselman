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

@Service
public class TreatmentScheduleService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final List<TreatmentSchedule> schedules = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong nextId = new java.util.concurrent.atomic.AtomicLong(7);

    public TreatmentScheduleService() {
        seedSchedules();
    }

    public List<TreatmentSchedule> listSchedules(String date, String keyword, String treatmentName, String status) {
        String normalizedDate = normalize(date);
        String normalizedKeyword = normalize(keyword).toLowerCase(Locale.ROOT);
        String normalizedTreatment = normalize(treatmentName);
        String normalizedStatus = normalize(status);

        return schedules.stream()
                .filter(schedule -> !StringUtils.hasText(normalizedDate)
                        || normalizedDate.equals(schedule.getTreatmentDate()))
                .filter(schedule -> !StringUtils.hasText(normalizedKeyword)
                        || schedule.getPatientName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || schedule.getWard().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(schedule -> !StringUtils.hasText(normalizedTreatment)
                        || normalizedTreatment.equals(schedule.getTreatmentName()))
                .filter(schedule -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equals(schedule.getStatus()))
                .toList();
    }

    public Map<String, Long> getSummary(String date) {
        String targetDate = StringUtils.hasText(date) ? date.trim() : LocalDate.now().toString();
        List<TreatmentSchedule> targetSchedules = listSchedules(targetDate, null, null, null);
        long reserved = targetSchedules.stream().filter(schedule -> "예약".equals(schedule.getStatus())).count();
        long completed = targetSchedules.stream().filter(schedule -> "치료완료".equals(schedule.getStatus())).count();
        long canceled = targetSchedules.stream().filter(schedule -> "예약취소".equals(schedule.getStatus())).count();
        return Map.of(
                "total", (long) targetSchedules.size(),
                "reserved", reserved,
                "completed", completed,
                "canceled", canceled);
    }

    public Map<String, Object> getDashboard(String date) {
        String targetDate = StringUtils.hasText(date) ? date.trim() : LocalDate.now().toString();
        List<TreatmentSchedule> targetSchedules = listSchedules(targetDate, null, null, null);
        return Map.of(
                "summary", getSummary(targetDate),
                "byTreatment", countBy(targetSchedules, TreatmentSchedule::getTreatmentName),
                "byWard", countBy(targetSchedules, TreatmentSchedule::getWard),
                "recent", targetSchedules.stream()
                        .sorted(Comparator.comparing(TreatmentSchedule::getStartTime))
                        .limit(6)
                        .toList());
    }

    public TreatmentSchedule updateStatus(Long id, String status) {
        String normalizedStatus = normalize(status);
        if (!List.of("예약", "치료완료", "예약취소").contains(normalizedStatus)) {
            throw new IllegalArgumentException("지원하지 않는 치료상태입니다.");
        }
        TreatmentSchedule schedule = findSchedule(id);
        schedule.setStatus(normalizedStatus);
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public TreatmentSchedule updateStartTime(Long id, String startTime) {
        String normalized = normalize(startTime);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("시작시간을 입력해주세요.");
        }
        if (normalized.length() == 4 && normalized.charAt(1) == ':') {
            normalized = "0" + normalized;
        }
        try {
            LocalTime parsed = LocalTime.parse(normalized);
            normalized = String.format("%02d:%02d", parsed.getHour(), parsed.getMinute());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시작시간은 HH:mm 형식이어야 합니다.");
        }
        TreatmentSchedule schedule = findSchedule(id);
        schedule.setStartTime(normalized);
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public TreatmentSchedule updateTextField(Long id, String field, String value) {
        TreatmentSchedule schedule = findSchedule(id);
        String normalizedValue = normalize(value);
        if (normalizedValue.length() > 1000) {
            throw new IllegalArgumentException("입력값은 1000자 이하로 입력해주세요.");
        }
        if ("treatmentInfo".equals(field)) {
            schedule.setTreatmentInfo(normalizedValue);
        } else if ("note".equals(field)) {
            schedule.setNote(normalizedValue);
        } else {
            throw new IllegalArgumentException("수정할 수 없는 항목입니다.");
        }
        sendScheduleChanged("UPDATED", schedule);
        return schedule;
    }

    public TreatmentSchedule createSchedule(String instCode, TreatmentScheduleRequest req) {
        if (!StringUtils.hasText(req.getTreatmentDate()) || !StringUtils.hasText(req.getStartTime())
                || !StringUtils.hasText(req.getPatientName()) || !StringUtils.hasText(req.getTreatmentName())) {
            throw new IllegalArgumentException("필수 항목을 입력해주세요.");
        }
        if (StringUtils.hasText(req.getStatus())
                && !List.of("예약", "치료완료", "예약취소").contains(req.getStatus().trim())) {
            throw new IllegalArgumentException("지원하지 않는 치료상태입니다.");
        }
        TreatmentSchedule schedule = new TreatmentSchedule(
                nextId.getAndIncrement(),
                req.getTreatmentDate().trim(),
                req.getStartTime().trim(),
                req.getPatientName().trim(),
                normalize(req.getWard()),
                req.getTreatmentName().trim(),
                normalize(req.getTreatmentOption()),
                StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "예약",
                normalize(req.getTreatmentInfo()),
                normalize(req.getNote()));
        schedules.add(schedule);
        sendScheduleChanged("CREATED", schedule);
        return schedule;
    }

    public TreatmentSchedule updateSchedule(Long id, TreatmentScheduleRequest req) {
        if (!StringUtils.hasText(req.getTreatmentDate()) || !StringUtils.hasText(req.getStartTime())
                || !StringUtils.hasText(req.getPatientName()) || !StringUtils.hasText(req.getTreatmentName())) {
            throw new IllegalArgumentException("필수 항목을 입력해주세요.");
        }
        if (StringUtils.hasText(req.getStatus())
                && !List.of("예약", "치료완료", "예약취소").contains(req.getStatus().trim())) {
            throw new IllegalArgumentException("지원하지 않는 치료상태입니다.");
        }
        findSchedule(id);
        schedules.removeIf(s -> s.getId().equals(id));
        TreatmentSchedule updated = new TreatmentSchedule(
                id,
                req.getTreatmentDate().trim(),
                req.getStartTime().trim(),
                req.getPatientName().trim(),
                normalize(req.getWard()),
                req.getTreatmentName().trim(),
                normalize(req.getTreatmentOption()),
                StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "예약",
                normalize(req.getTreatmentInfo()),
                normalize(req.getNote()));
        schedules.add(updated);
        sendScheduleChanged("UPDATED", updated);
        return updated;
    }

    public void deleteSchedule(Long id) {
        TreatmentSchedule captured = findSchedule(id);
        schedules.removeIf(s -> s.getId().equals(id));
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

    private TreatmentSchedule findSchedule(Long id) {
        return schedules.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("치료 스케줄을 찾을 수 없습니다."));
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

    private void seedSchedules() {
        String today = LocalDate.now().toString();
        schedules.add(new TreatmentSchedule(1L, today, "08:30", "김진수(외)", "외래", "파동", "J", "예약", "싸이2+압2+메90+pj월2", "고주파 호흡 부담으로 중단"));
        schedules.add(new TreatmentSchedule(2L, today, "09:20", "이해섭(1)", "1병동", "고주파(온코)", "K", "치료완료", "고주파 주2+싸이 주1", "항암 전후 서비스"));
        schedules.add(new TreatmentSchedule(3L, today, "10:10", "신금희(1)", "1병동", "림프1", "C", "예약", "pj주3(도수2,림1)", "항암 후 영양제 서비스"));
        schedules.add(new TreatmentSchedule(4L, today, "11:00", "이영애(1)", "1병동", "도수", "", "예약취소", "메시마90포+PJ주2", "재입원"));
        schedules.add(new TreatmentSchedule(5L, today, "13:30", "박정심(1)", "1병동", "페인잼머", "S", "예약", "고2+싸이 월2+압2", "실비 확인 필요"));
        schedules.add(new TreatmentSchedule(6L, today, "14:20", "서경례(1)", "1병동", "파동", "J", "치료완료", "싸이 주2+압F 주3", "자궁내막암 수술 후"));
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
