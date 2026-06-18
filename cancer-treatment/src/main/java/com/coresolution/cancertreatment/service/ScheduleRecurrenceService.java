package com.coresolution.cancertreatment.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.ScheduleRecurrenceRequest;
import com.coresolution.cancertreatment.repository.ScheduleRecurrenceRepository;
import com.coresolution.cancertreatment.repository.TreatmentScheduleRepository;

/**
 * Registers one-off and recurring treatment schedules.
 * Recurring registration is a single atomic unit: the rule row plus every materialized
 * occurrence row are inserted in one transaction — any failure rolls back all of them.
 */
@Service
public class ScheduleRecurrenceService {

    /** Upper bound for a single recurring registration (5/week * ~40 weeks). Guards against runaway input. */
    static final int MAX_OCCURRENCE_COUNT = 200;
    private static final int FULL_WEEK_MASK = 0b1111111; // 127

    private final ScheduleRecurrenceRepository recurrenceRepository;
    private final TreatmentScheduleRepository scheduleRepository;

    public ScheduleRecurrenceService(
            ScheduleRecurrenceRepository recurrenceRepository,
            TreatmentScheduleRepository scheduleRepository) {
        this.recurrenceRepository = recurrenceRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public Result createSchedules(String instCode, String createdBy, ScheduleRecurrenceRequest req) {
        String inst = requireText(instCode, "병원 코드가 없습니다.");
        validateBase(req);
        LocalDate startDate = parseDate(req.getStartDate());
        String startTime = req.getStartTime().trim();
        String patientName = req.getPatientName().trim();
        String treatmentName = req.getTreatmentName().trim();
        String status = StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "예약";

        if (!req.isRepeat()) {
            scheduleRepository.createOccurrence(
                    inst, req.getPatientId(), req.getTreatmentRoomId(), req.getSeatId(), null,
                    startDate.toString(), startTime, patientName, req.getWard(),
                    treatmentName, req.getTreatmentOption(), status,
                    req.getTreatmentInfo(), req.getNote());
            return new Result(null, 1, null, startDate.toString());
        }

        int mask = req.getWeekdayMask() == null ? 0 : req.getWeekdayMask();
        int count = req.getOccurrenceCount() == null ? 0 : req.getOccurrenceCount();
        validateRecurrence(mask, count);

        List<LocalDate> dates = generateDates(startDate, mask, count);

        Long ruleId = recurrenceRepository.createRule(
                inst, req.getTreatmentRoomId(), req.getSeatId(), req.getPatientId(),
                mask, startDate.toString(), count, startTime,
                treatmentName, req.getTreatmentOption(), req.getTreatmentInfo(), req.getNote(), createdBy);

        for (LocalDate date : dates) {
            scheduleRepository.createOccurrence(
                    inst, req.getPatientId(), req.getTreatmentRoomId(), req.getSeatId(), ruleId,
                    date.toString(), startTime, patientName, req.getWard(),
                    treatmentName, req.getTreatmentOption(), status,
                    req.getTreatmentInfo(), req.getNote());
        }

        LocalDate lastDate = dates.get(dates.size() - 1);
        String warning = boundaryWarning(startDate, lastDate, req.getPrescriptionWeeks());
        return new Result(ruleId, dates.size(), warning, lastDate.toString());
    }

    private void validateBase(ScheduleRecurrenceRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("스케줄 정보를 입력해주세요.");
        }
        requireText(req.getPatientName(), "환자명을 입력해주세요.");
        requireText(req.getTreatmentName(), "치료종류를 입력해주세요.");
        requireText(req.getStartDate(), "시작일을 입력해주세요.");
        requireText(req.getStartTime(), "시작시간을 입력해주세요.");
        parseTime(req.getStartTime());
    }

    private void validateRecurrence(int mask, int count) {
        if (mask <= 0 || mask > FULL_WEEK_MASK) {
            throw new IllegalArgumentException("반복 요일을 한 개 이상 선택해주세요.");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("반복 횟수는 1 이상이어야 합니다.");
        }
        if (count > MAX_OCCURRENCE_COUNT) {
            throw new IllegalArgumentException("반복 횟수는 " + MAX_OCCURRENCE_COUNT + "회를 초과할 수 없습니다.");
        }
    }

    /** weekday_mask: bit0=Mon … bit6=Sun. Collects matching dates from startDate until count is reached. */
    private List<LocalDate> generateDates(LocalDate startDate, int mask, int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        LocalDate cursor = startDate;
        int safetyLimit = count * 7 + 14; // bounded scan; count is already <= MAX_OCCURRENCE_COUNT
        for (int scanned = 0; dates.size() < count && scanned < safetyLimit; scanned++) {
            int bit = cursor.getDayOfWeek().getValue() - 1; // Mon=0 … Sun=6
            if ((mask & (1 << bit)) != 0) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        if (dates.size() < count) {
            throw new IllegalStateException("반복 일정 생성에 실패했습니다.");
        }
        return dates;
    }

    private String boundaryWarning(LocalDate startDate, LocalDate lastDate, Integer prescriptionWeeks) {
        if (prescriptionWeeks == null || prescriptionWeeks <= 0) {
            return null;
        }
        LocalDate boundary = startDate.plusDays((long) prescriptionWeeks * 7 - 1);
        if (lastDate.isAfter(boundary)) {
            return "이 반복 일정은 예상 치료기간(" + prescriptionWeeks + "주)을 넘는 일정을 포함합니다. 처방주수를 확인하세요.";
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시작일 형식이 올바르지 않습니다.");
        }
    }

    private void parseTime(String value) {
        try {
            LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시작시간은 HH:mm 형식이어야 합니다.");
        }
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    public record Result(Long recurrenceId, int createdCount, String warning, String lastDate) {
    }
}
