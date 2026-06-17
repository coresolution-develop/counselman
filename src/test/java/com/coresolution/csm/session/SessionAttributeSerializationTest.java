package com.coresolution.csm.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.vo.Userdata;

/**
 * Regression guard for session externalization (Spring Session JDBC, 2단계).
 *
 * 세션을 외부 저장소(JDBC)에 보관하면 모든 세션 속성이 직렬화된다.
 * LoginController가 로그인 직후 세션에 담는 값은:
 *   - "userInfo"               : {@link Userdata}
 *   - SPRING_SECURITY_CONTEXT  : {@link SecurityContextImpl}
 *       (principal=String, authorities=SimpleGrantedAuthority, details={@link InstDetails})
 * 이 중 하나라도 직렬화 불가가 되면 로그인 직후 세션 저장(JDBC)이 깨진다.
 * 향후 누군가 비직렬화 필드를 추가하면 이 테스트가 빌드에서 잡아낸다.
 */
class SessionAttributeSerializationTest {

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T original) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(original);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            return (T) ois.readObject();
        }
    }

    @Test
    void userdata_isSerializable() throws Exception {
        Userdata u = new Userdata();
        u.setUs_col_01(7);
        u.setUs_col_02("tester");
        u.setUs_col_05("CoreSolution");
        u.setUs_col_08(1);

        Userdata copy = roundTrip(u);

        assertThat(copy.getUs_col_01()).isEqualTo(7);
        assertThat(copy.getUs_col_02()).isEqualTo("tester");
        assertThat(copy.getUs_col_05()).isEqualTo("CoreSolution");
        assertThat(copy.getUs_col_08()).isEqualTo(1);
    }

    @Test
    void securityContext_storedAtLogin_isSerializable() throws Exception {
        var authToken = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authToken.setDetails(new InstDetails("CORE"));
        SecurityContextImpl ctx = new SecurityContextImpl(authToken);

        SecurityContextImpl copy = roundTrip(ctx);

        assertThat(copy.getAuthentication().getName()).isEqualTo("tester");
        assertThat(copy.getAuthentication().getAuthorities())
                .extracting(Object::toString).contains("ROLE_USER");
        assertThat(((InstDetails) copy.getAuthentication().getDetails()).normalized()).isEqualTo("CORE");
    }
}
