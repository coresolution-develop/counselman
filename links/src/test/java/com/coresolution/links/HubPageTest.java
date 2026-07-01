package com.coresolution.links;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.coresolution.links.serivce.CompanyLinkService;
import com.coresolution.links.serivce.HubCustomLinkService;
import com.coresolution.links.serivce.HubFavoriteService;
import com.coresolution.links.serivce.HubHistoryService;
import com.coresolution.links.serivce.HubMemberService;
import com.coresolution.links.serivce.HubRememberService;

/**
 * Smoke test for the standalone hub app: context wiring (controllers,
 * interceptors, security), public page rendering, CSRF auto-injection into the
 * login form (the mechanism that lets /hub/login POST work without an explicit
 * token), and member-gating of /hub/me. All DB-touching services are mocked so
 * no MySQL is needed; an H2 DataSource lets the context load.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:linkshub;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class HubPageTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean CompanyLinkService companyLinkService;
    @MockitoBean HubMemberService hubMemberService;
    @MockitoBean HubFavoriteService hubFavoriteService;
    @MockitoBean HubCustomLinkService hubCustomLinkService;
    @MockitoBean HubHistoryService hubHistoryService;
    @MockitoBean HubRememberService hubRememberService;

    @Test
    void publicHubPageRenders() throws Exception {
        when(companyLinkService.listActiveLinks()).thenReturn(List.of());
        when(companyLinkService.listCategories()).thenReturn(List.of());

        mockMvc.perform(get("/links"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/assets/css/hub.css")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPageHasAutoInjectedCsrfField() throws Exception {
        // The login form uses th:action; Spring Security's CsrfRequestDataValueProcessor
        // must inject a hidden _csrf field so the POST is not rejected with 403.
        mockMvc.perform(get("/hub/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void protectedMePageRedirectsToLoginWhenAnonymous() throws Exception {
        mockMvc.perform(get("/hub/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hub/login"));
    }
}
