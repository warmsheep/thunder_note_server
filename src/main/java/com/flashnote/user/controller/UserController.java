package com.flashnote.user.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/profile")
    public ApiResponse<UserProfile> getProfile(Authentication authentication) {
        return ApiResponse.success(userService.getProfile(authentication.getName()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfile> updateProfile(Authentication authentication,
                                                  @RequestBody UserProfile profile) {
        return ApiResponse.success(userService.updateProfile(authentication.getName(), profile));
    }

    @PutMapping("/avatar")
    public ApiResponse<String> updateAvatar(Authentication authentication,
                                         @RequestBody java.util.Map<String, String> body) {
        String avatarUrl = body.get("avatar");
        return ApiResponse.success(userService.updateAvatar(authentication.getName(), avatarUrl));
    }
}
