package com.flashnote.auth.security;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedAccessToken_doesNotRotateRefreshTokenSilently() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setStatus(1);

        when(jwtUtil.validateToken("access-token", "access")).thenReturn(true);
        when(jwtUtil.getUserId("access-token")).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).generateRefreshToken(7L, "alice");
    }
}
