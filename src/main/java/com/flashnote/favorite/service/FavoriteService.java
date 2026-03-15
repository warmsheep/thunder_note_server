package com.flashnote.favorite.service;

import com.flashnote.favorite.dto.FavoriteMessageItem;

import java.util.List;

public interface FavoriteService {
    List<FavoriteMessageItem> listFavorites(String username);

    FavoriteMessageItem addFavorite(String username, Long messageId);

    void removeFavorite(String username, Long messageId);
}
