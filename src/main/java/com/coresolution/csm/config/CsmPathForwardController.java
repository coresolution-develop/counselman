package com.coresolution.csm.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CsmPathForwardController {

    private static final String PREFIX = "/csm";

    @RequestMapping({ "/csm", "/csm/" })
    public String forwardRoot() {
        return "forward:/login";
    }

    @RequestMapping("/csm/**")
    public String forwardPrefixedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

        String target = path;
        while (target.startsWith(PREFIX + "/")) {
            target = target.substring(PREFIX.length());
        }

        if (target.equals(PREFIX)) {
            target = "/login";
        } else if (target.isBlank()) {
            target = "/login";
        } else if (!target.startsWith("/")) {
            target = "/" + target;
        }

        return "forward:" + target;
    }
}
