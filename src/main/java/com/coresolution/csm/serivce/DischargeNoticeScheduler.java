package com.coresolution.csm.serivce;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coresolution.csm.mapper.CsmMapper;
import com.coresolution.csm.vo.Instdata;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DischargeNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DischargeNoticeScheduler.class);

    private final CsmMapper csmMapper;
    private final RoomBoardService roomBoardService;

    /**
     * AM 퇴원: 당일 13:00에 PLANNED → COMPLETED 자동 처리
     */
    @Scheduled(cron = "0 0 13 * * *")
    public void autoCompleteAmDischarges() {
        autoComplete("AM", LocalDate.now());
    }

    /**
     * PM 퇴원: 다음날 00:05에 전날 PLANNED → COMPLETED 자동 처리
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void autoCompletePmDischarges() {
        autoComplete("PM", LocalDate.now().minusDays(1));
    }

    private void autoComplete(String dischargeTime, LocalDate targetDate) {
        for (Instdata inst : csmMapper.coreInstSelect()) {
            String instCode = inst.getId_col_03();
            if (instCode == null || instCode.isBlank() || "core".equalsIgnoreCase(instCode)) {
                continue;
            }
            try {
                int count = roomBoardService.autoCompleteByTime(instCode, targetDate, dischargeTime);
                if (count > 0) {
                    log.info("[auto-discharge] {} completed: inst={} date={} count={}",
                            dischargeTime, instCode, targetDate, count);
                }
            } catch (Exception e) {
                log.warn("[auto-discharge] {} failed: inst={} error={}", dischargeTime, instCode, e.getMessage());
            }
        }
    }
}
