package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CompanyLinkServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void createLink_acceptsHttpUrlAndStoresLink() {
        CompanyLinkService service = new CompanyLinkService(jdbcTemplate);
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(3L);

        service.createLink("상담 CRM", "https://crm.example.com", "상담 시스템", "운영", 10, "public");

        verify(jdbcTemplate).update(
                contains("INSERT INTO csm.company_link"),
                eq("상담 CRM"),
                eq("https://crm.example.com"),
                eq("상담 시스템"),
                eq("운영"),
                eq(10),
                eq("public"),
                eq("public"));
    }

    @Test
    void createLink_rejectsJavascriptUrl() {
        CompanyLinkService service = new CompanyLinkService(jdbcTemplate);

        assertThatThrownBy(() -> service.createLink("위험 링크", "javascript:alert(1)", "", "", 0, "public"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http 또는 https");
    }

    @Test
    void createLink_acceptsHostWithUnderscore() {
        CompanyLinkService service = new CompanyLinkService(jdbcTemplate);
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(4L);

        service.createLink("스테이 결제", "https://stay_djm.cspay.co.kr/products", "", "결제", 0, "public");

        verify(jdbcTemplate).update(
                contains("INSERT INTO csm.company_link"),
                eq("스테이 결제"),
                eq("https://stay_djm.cspay.co.kr/products"),
                eq(null),
                eq("결제"),
                eq(0),
                eq("public"),
                eq("public"));
    }

    @Test
    void deleteLink_softDeletesActiveRow() {
        CompanyLinkService service = new CompanyLinkService(jdbcTemplate);
        when(jdbcTemplate.update(
                contains("UPDATE csm.company_link SET use_yn = 'N'"),
                eq("public"),
                eq(5L)))
                .thenReturn(1);

        service.deleteLink(5L, "public");

        verify(jdbcTemplate).update(
                contains("UPDATE csm.company_link SET use_yn = 'N'"),
                eq("public"),
                eq(5L));
    }

    @Test
    void updateLink_updatesActiveRow() {
        CompanyLinkService service = new CompanyLinkService(jdbcTemplate);
        when(jdbcTemplate.update(
                contains("UPDATE csm.company_link"),
                eq("상담 CRM"),
                eq("https://crm.example.com"),
                eq("상담 시스템"),
                eq("운영"),
                eq(10),
                eq("public"),
                eq(5L)))
                .thenReturn(1);

        service.updateLink(5L, "상담 CRM", "https://crm.example.com", "상담 시스템", "운영", 10, "public");

        verify(jdbcTemplate).update(
                contains("UPDATE csm.company_link"),
                eq("상담 CRM"),
                eq("https://crm.example.com"),
                eq("상담 시스템"),
                eq("운영"),
                eq(10),
                eq("public"),
                eq(5L));
    }
}
