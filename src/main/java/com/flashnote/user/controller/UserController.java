package com.flashnote.user.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.user.dto.AvatarUpdateRequest;
import com.flashnote.user.dto.ContactSearchUserDto;
import com.flashnote.user.dto.ContactUserDto;
import com.flashnote.user.dto.FriendRequestActionRequest;
import com.flashnote.user.dto.FriendRequestCreateRequest;
import com.flashnote.user.dto.FriendRequestDto;
import com.flashnote.user.dto.UserProfileResponse;
import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(Authentication authentication) {
        return ApiResponse.success(toResponse(userService.getProfile(authentication.getName())));
    }

    @GetMapping("/contacts")
    public ApiResponse<List<ContactUserDto>> listContacts(Authentication authentication) {
        return ApiResponse.success(userService.listContacts(authentication.getName()));
    }

    @GetMapping("/contacts/requests")
    public ApiResponse<List<FriendRequestDto>> listFriendRequests(Authentication authentication) {
        return ApiResponse.success(userService.listPendingRequests(authentication.getName()));
    }

    @GetMapping("/contacts/requests/count")
    public ApiResponse<Long> countFriendRequests(Authentication authentication) {
        return ApiResponse.success(userService.countPendingRequests(authentication.getName()));
    }

    @PostMapping("/contacts/request")
    public ApiResponse<Void> sendFriendRequest(Authentication authentication,
                                               @RequestBody FriendRequestCreateRequest request) {
        userService.sendFriendRequest(authentication.getName(), request == null ? null : request.getTargetUserId());
        return ApiResponse.success("Requested", null);
    }

    @PostMapping("/contacts/request/accept")
    public ApiResponse<Void> acceptFriendRequest(Authentication authentication,
                                                 @RequestBody FriendRequestActionRequest request) {
        userService.acceptFriendRequest(authentication.getName(), request == null ? null : request.getRequestId());
        return ApiResponse.success("Accepted", null);
    }

    @PostMapping("/contacts/request/reject")
    public ApiResponse<Void> rejectFriendRequest(Authentication authentication,
                                                 @RequestBody FriendRequestActionRequest request) {
        userService.rejectFriendRequest(authentication.getName(), request == null ? null : request.getRequestId());
        return ApiResponse.success("Rejected", null);
    }

    @DeleteMapping("/contacts/request/{requestId}")
    public ApiResponse<Void> cancelFriendRequest(Authentication authentication,
                                                 @PathVariable Long requestId) {
        userService.cancelFriendRequest(authentication.getName(), requestId);
        return ApiResponse.success("Cancelled", null);
    }

    @DeleteMapping("/contacts/{contactUserId}")
    public ApiResponse<Void> deleteContact(Authentication authentication,
                                           @PathVariable Long contactUserId) {
        userService.removeContact(authentication.getName(), contactUserId);
        return ApiResponse.success("Deleted", null);
    }

    @GetMapping("/contacts/search")
    public ApiResponse<List<ContactSearchUserDto>> searchContacts(Authentication authentication,
                                                                  @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.success(userService.searchUsers(authentication.getName(), keyword));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(Authentication authentication,
                                                   @RequestBody UserProfile profile) {
        return ApiResponse.success(toResponse(userService.updateProfile(authentication.getName(), profile)));
    }

    @PutMapping("/avatar")
    public ApiResponse<String> updateAvatar(Authentication authentication,
                                          @Valid @RequestBody AvatarUpdateRequest request) {
        return ApiResponse.success(userService.updateAvatar(authentication.getName(), request.getAvatar()));
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        UserProfileResponse response = new UserProfileResponse();
        response.setId(profile.getId());
        response.setUserId(profile.getUserId());
        response.setBio(profile.getBio());
        response.setPreferencesJson(profile.getPreferencesJson());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        response.setAvatar(profile.getAvatar());
        response.setNickname(profile.getNickname());
        return response;
    }
}
