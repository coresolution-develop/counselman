package com.coresolution.csm.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.*;

import com.coresolution.csm.mapper.CsmMapper;
import com.coresolution.csm.vo.Userdata;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/nav-order")
@RequiredArgsConstructor
public class NavOrderApiController {

    private final CsmMapper cs;

    @GetMapping
    public List<Map<String, Object>> get(HttpSession session) {
        String inst     = (String) session.getAttribute("inst");
        Userdata u      = (Userdata) session.getAttribute("userInfo");
        if (inst == null || u == null) return List.of();
        return cs.getUserNavOrder(inst, String.valueOf(u.getUs_col_01()));
    }

    @PostMapping
    public Map<String, Object> save(
            @RequestBody List<Map<String, Object>> order,
            HttpSession session) {
        String inst = (String) session.getAttribute("inst");
        Userdata u  = (Userdata) session.getAttribute("userInfo");
        if (inst == null || u == null)
            return Map.of("success", false, "message", "세션 없음");
        if (order == null || order.isEmpty())
            return Map.of("success", false, "message", "데이터 없음");

        String username = String.valueOf(u.getUs_col_01());
        cs.deleteUserNavOrder(inst, username);
        for (Map<String, Object> item : order) {
            String navKey = (String) item.get("nav_key");
            int sortOrder = ((Number) item.get("sort_order")).intValue();
            if (navKey != null && !navKey.isBlank())
                cs.upsertNavOrder(inst, username, navKey, sortOrder);
        }
        return Map.of("success", true);
    }
}
