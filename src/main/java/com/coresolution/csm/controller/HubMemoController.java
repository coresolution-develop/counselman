package com.coresolution.csm.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.csm.serivce.HubMemoService;
import com.coresolution.csm.vo.HubMemberSession;
import com.coresolution.csm.web.HubSessions;

import jakarta.servlet.http.HttpSession;

/**
 * 허브 개인 메모장 저장 (AJAX). 응답: {"ok": true}.
 *
 * <p>인터셉터가 1차로 막지만, 컨트롤러 진입부에서도 세션을 직접 가드하고
 * member_id를 세션에서만 취한다(가드레일 ①). 요청 본문의 회원 식별자는 신뢰하지 않는다.
 */
@RestController
public class HubMemoController {

    private final HubMemoService hubMemoService;

    public HubMemoController(HubMemoService hubMemoService) {
        this.hubMemoService = hubMemoService;
    }

    @PostMapping("/hub/me/memo")
    public ResponseEntity<Map<String, Object>> save(
            @RequestParam(value = "content", required = false, defaultValue = "") String content,
            HttpSession session) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요합니다."));
        }
        try {
            hubMemoService.save(member.getId(), content);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
