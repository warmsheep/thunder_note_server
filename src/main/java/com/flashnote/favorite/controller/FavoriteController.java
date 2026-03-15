package com.flashnote.favorite.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.favorite.dto.FavoriteMessageItem;
import com.flashnote.favorite.service.FavoriteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/list")
    public ApiResponse<List<FavoriteMessageItem>> list(Authentication authentication) {
        return ApiResponse.success(favoriteService.listFavorites(authentication.getName()));
    }

    @PostMapping("/{messageId}")
    public ApiResponse<FavoriteMessageItem> create(Authentication authentication, @PathVariable Long messageId) {
        return ApiResponse.success(favoriteService.addFavorite(authentication.getName(), messageId));
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long messageId) {
        favoriteService.removeFavorite(authentication.getName(), messageId);
        return ApiResponse.success("Deleted", null);
    }
}
