package com.coresolution.mediplat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 운전자 모바일 진입 페이지. QR({@code {base}/fleet/t/{qrToken}})을 스캔하면 이 페이지가 열리고,
 * 페이지 JS가 기기 인식(/fleet/me) → 등록 → 차량 상태(by-token) → 출발/도착 흐름을 진행한다.
 *
 * <p>인증은 FleetDeviceInterceptor가 쿠키로 세션을 복원하는 경량 기기 신원이다(관리자 로그인 무관).
 */
@Controller
public class FleetPageController {

    @GetMapping("/fleet/t/{qrToken}")
    public String driverEntry(@PathVariable String qrToken, Model model) {
        model.addAttribute("qrToken", qrToken);
        return "fleet-driver";
    }
}
