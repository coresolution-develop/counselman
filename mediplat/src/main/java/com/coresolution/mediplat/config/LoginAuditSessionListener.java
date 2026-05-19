package com.coresolution.mediplat.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.coresolution.mediplat.controller.MediplatController;
import com.coresolution.mediplat.service.PlatformStoreService;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

@Configuration
public class LoginAuditSessionListener {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> loginAuditListenerRegistration(
            PlatformStoreService storeService) {
        ServletListenerRegistrationBean<HttpSessionListener> registration = new ServletListenerRegistrationBean<>();
        registration.setListener(new Listener(storeService));
        return registration;
    }

    static final class Listener implements HttpSessionListener {

        private final PlatformStoreService storeService;

        Listener(PlatformStoreService storeService) {
            this.storeService = storeService;
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent event) {
            try {
                Object value = event.getSession().getAttribute(MediplatController.SESSION_LOGIN_AUDIT_ID);
                if (value instanceof Long auditId) {
                    storeService.recordLogout(auditId);
                }
            } catch (IllegalStateException ignored) {
                // session already invalidated; attribute lookup may fail on some containers
            }
        }
    }
}
