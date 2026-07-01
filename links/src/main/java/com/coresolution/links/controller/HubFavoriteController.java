package com.coresolution.links.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.links.serivce.HubFavoriteService;
import com.coresolution.links.vo.HubMemberSession;
import com.coresolution.links.web.HubSessions;

import jakarta.servlet.http.HttpSession;

/**
 * 공개 허브 ★ 즐겨찾기 토글 (AJAX). 응답: {"favorited": true|false}.
 *
 * <p>인터셉터가 1차로 막지만, 컨트롤러 진입부에서도 세션을 직접 가드하고
 * member_id를 세션에서만 취한다(가드레일 ①). 요청 본문의 회원 식별자는 신뢰하지 않는다.
 */
@RestController
public class HubFavoriteController {

    private final HubFavoriteService hubFavoriteService;

    public HubFavoriteController(HubFavoriteService hubFavoriteService) {
        this.hubFavoriteService = hubFavoriteService;
    }

    @PostMapping("/hub/me/favorites/toggle")
    public ResponseEntity<Map<String, Object>> toggle(
            @RequestParam("linkId") long linkId,
            HttpSession session) {
        HubMemberSession member = HubSessions.current(session);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요합니다."));
        }
        try {
            boolean favorited = hubFavoriteService.toggle(member.getId(), linkId);
            return ResponseEntity.ok(Map.of("favorited", favorited));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
