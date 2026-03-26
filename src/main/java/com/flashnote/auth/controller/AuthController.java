package com.flashnote.auth.controller;

import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RefreshTokenRequest;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.dto.ChangePasswordRequest;
import com.flashnote.auth.service.AuthService;
import com.flashnote.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("Register success", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        authService.logout(authHeader);
        return ApiResponse.success("Logout success", null);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(Authentication authentication,
                                         @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ApiResponse.success("Password changed", null);
    }
}
