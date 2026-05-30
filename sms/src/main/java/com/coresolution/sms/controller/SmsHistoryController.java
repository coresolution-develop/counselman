package com.coresolution.sms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.sms.model.HistoryQuery;
import com.coresolution.sms.model.SessionUser;
import com.coresolution.sms.repository.SmsRepository;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/sms")
public class SmsHistoryController {

    private final SmsRepository repository;

    public SmsHistoryController(SmsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/history")
    public Map<String, Object> history(
            HttpSession session,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "fail", required = false) String fail,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "perPageNum", defaultValue = "10") int perPageNum) {
        SessionUser user = SmsSession.require(session);
        HistoryQuery query = new HistoryQuery(user.getInstCode(), type, keyword, fail, page, perPageNum);
        List<Map<String, Object>> rows = repository.selectTransmissionHistory(query);
        int total = repository.smsCnt(query);
        return Map.of(
                "rows", rows,
                "total", total,
                "page", query.getPage(),
                "perPageNum", query.getPerPageNum());
    }
}
