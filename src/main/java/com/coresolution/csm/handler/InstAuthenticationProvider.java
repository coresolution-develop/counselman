package com.coresolution.csm.handler;

import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.AjaxResponse;
import com.coresolution.csm.vo.Userdata;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InstAuthenticationProvider implements AuthenticationProvider {

    private final CsmAuthService cs;
    private final AES128 aes128; // CryptoConfig에서 Bean 주입

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String rawPassword = (String) authentication.getCredentials();

        // 1) 클라이언트가 보낸 inst는 '입력값'일 뿐 → DB로 정규화/검증
        Object details = authentication.getDetails();
        String inputInst = (details instanceof InstDetails d) ? d.normalized() : null;

        // DB 존재 여부/별칭 매핑 등은 Service로 위임
        String inst = cs.resolveInst(inputInst);
        if (inst == null)
            throw new BadCredentialsException("유효하지 않은 기관코드입니다.");

        // 이후 enc 비번으로 카운트/정보 조회
        String enc = aes128.encrypt(rawPassword);
        int cnt = cs.loginCount(inst, username, enc);
        if (cnt < 1)
            throw new BadCredentialsException("아이디/비밀번호/기관코드가 올바르지 않습니다.");

        // 정규화된 inst를 다시 details에 태워둠(세션/후속 로직에서 사용)
        var auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new InstDetails(inst));
        return auth;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
