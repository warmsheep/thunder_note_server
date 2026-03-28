package com.flashnote.common.ratelimit;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.common.utils.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final int LOGIN_MAX_ATTEMPTS = 10;
    private static final int LOGIN_WINDOW_SECONDS = 60;
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );

    private final RedisUtil redisUtil;

    public RateLimitInterceptor(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!PROTECTED_PATHS.contains(request.getRequestURI())) {
            return true;
        }
        String ip = resolveClientIp(request);
        String key = "ratelimit:auth:" + ip;

        String countStr = redisUtil.get(key);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);

        if (count >= LOGIN_MAX_ATTEMPTS) {
            write429(response, "Too many login attempts, please try again later");
            return false;
        }

        redisUtil.set(key, String.valueOf(count + 1), LOGIN_WINDOW_SECONDS);
        return true;
    }

    private void write429(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":42900,\"message\":\"" + message + "\",\"data\":null}");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) {
            return xrip.trim();
        }
        return request.getRemoteAddr();
    }
}
