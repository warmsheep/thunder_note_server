package com.flashnote.auth.security;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.utils.RedisUtil;
import com.flashnote.common.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper, RedisUtil redisUtil) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.redisUtil = redisUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token, "access")) {
                Long userId = jwtUtil.getUserId(token);
                User user = userId == null ? null : userMapper.selectById(userId);

                if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    user.getUsername(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    tryAutoRenewByActivity(userId, user.getUsername());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void tryAutoRenewByActivity(Long userId, String username) {
        if (userId == null || username == null || username.isBlank()) {
            return;
        }

        String refreshKey = "auth:refresh:" + userId;
        String sessionStartKey = "auth:session:start:" + userId;
        String refreshToken = redisUtil.get(refreshKey);
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        long sessionStartMillis = resolveSessionStartMillis(userId, refreshToken);
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - sessionStartMillis);
        long maxDuration = jwtUtil.getMaxSessionDurationMillis();
        if (elapsed >= maxDuration) {
            redisUtil.delete(refreshKey);
            redisUtil.delete(sessionStartKey);
            return;
        }

        long remainingMillis = Math.max(1L, maxDuration - elapsed);
        long remainingRefreshSeconds = redisUtil.getExpireSeconds(refreshKey);
        if (remainingRefreshSeconds > jwtUtil.getAutoRenewThresholdMillis() / 1000) {
            return;
        }

        long refreshTtlMillis = Math.max(1L, Math.min(jwtUtil.getRefreshExpirationMillis(), remainingMillis));
        long refreshTtlSeconds = Math.max(1L, refreshTtlMillis / 1000);
        String renewedRefreshToken = jwtUtil.generateRefreshToken(userId, username, refreshTtlMillis);
        redisUtil.set(refreshKey, renewedRefreshToken, refreshTtlSeconds);
        redisUtil.set(sessionStartKey, String.valueOf(sessionStartMillis), Math.max(1L, remainingMillis / 1000));
    }

    private long resolveSessionStartMillis(Long userId, String refreshToken) {
        String cachedStart = redisUtil.get("auth:session:start:" + userId);
        if (cachedStart != null) {
            try {
                long parsed = Long.parseLong(cachedStart);
                if (parsed > 0L) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        long issuedAt = jwtUtil.getIssuedAtMillis(refreshToken);
        return issuedAt > 0L ? issuedAt : System.currentTimeMillis();
    }
}
