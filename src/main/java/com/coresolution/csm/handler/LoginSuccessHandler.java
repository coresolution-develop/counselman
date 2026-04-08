package com.coresolution.csm.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.coresolution.csm.config.InstDetails;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
            Authentication auth) throws IOException, ServletException {
        Object d = auth.getDetails();
        if (d instanceof InstDetails id && id.normalized() != null) {
            req.getSession().setAttribute("inst", id.normalized());
        }
        super.onAuthenticationSuccess(req, res, auth);
    }

}
