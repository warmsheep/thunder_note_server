package com.flashnote.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ApiLoggingSupport {
    private ApiLoggingSupport() {
    }

    public static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    public static String resolveApiName(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN UNKNOWN";
        }
        return request.getMethod() + " " + request.getRequestURI();
    }
}
