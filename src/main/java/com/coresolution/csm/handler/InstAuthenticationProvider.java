package com.coresolution.csm.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.serivce.PermissionResolver;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Userdata;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class InstAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(InstAuthenticationProvider.class);

    private final CsmAuthService cs;
    private final AES128 aes128;
    private final PermissionResolver permissionResolver;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String rawPassword = (String) authentication.getCredentials();
        log.warn("[AUTH-ENTRY] InstAuthenticationProvider.authenticate() called for user={}", username);

        // 1) 클라이언트가 보낸 inst는 '입력값'일 뿐 → DB로 정규화/검증
        Object details = authentication.getDetails();
        String inputInst = (details instanceof InstDetails d) ? d.normalized() : null;

        // DB 존재 여부/별칭 매핑 등은 Service로 위임
        String inst = cs.resolveInst(inputInst);
        if (inst == null)
            throw new BadCredentialsException("유효하지 않은 기관코드입니다.");
        if (!cs.isInstitutionAvailable(inst))
            throw new DisabledException("현재 기관은 CounselMan 사용이 중지되었습니다.");

        // 이후 enc 비번으로 카운트/정보 조회
        String enc = aes128.encrypt(rawPassword);
        int cnt = cs.loginCount(inst, username, enc);
        if (cnt < 1)
            throw new BadCredentialsException("아이디/비밀번호/기관코드가 올바르지 않습니다.");
        Userdata info = cs.loadUserInfo(inst, username);
        if (!cs.isUserAvailable(info))
            throw new DisabledException("사용 가능한 계정이 아닙니다.");

        // 권한 코드 → GrantedAuthority 변환
        List<GrantedAuthority> authorities = buildAuthorities(info, inst);

        var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        auth.setDetails(new InstDetails(inst));
        return auth;
    }

    private List<GrantedAuthority> buildAuthorities(Userdata info, String inst) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        if (info == null) return authorities;

        Integer col08 = info.getUs_col_08();
        long userId = info.getUs_col_01();

        // PLATFORM_ADMIN (us_col_08=0) — 모든 권한 부여
        if (col08 != null && col08 == 0) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
            for (String code : permissionResolver.allPermissions()) {
                authorities.add(new SimpleGrantedAuthority(code));
            }
            return authorities;
        }

        // INST_ADMIN (us_col_08=1) 또는 일반 사용자 — 역할 기반 권한
        if (col08 != null && col08 == 1) {
            authorities.add(new SimpleGrantedAuthority("ROLE_INST_ADMIN"));
        }

        try {
            Set<String> perms = permissionResolver.resolveUserPermissions(userId, inst);
            for (String code : perms) {
                authorities.add(new SimpleGrantedAuthority(code));
            }
        } catch (Exception e) {
            // 권한 테이블이 없는 경우(초기 설치 등) 기존 동작 유지
        }

        log.warn("[AUTH] user={} inst={} us_col_08={} authorities={}", info.getUs_col_02(), inst, info.getUs_col_08(), authorities);
        return authorities;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
