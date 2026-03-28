package com.flashnote.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.constant.NoteConstants;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.favorite.dto.FavoriteMessageItem;
import com.flashnote.favorite.entity.FavoriteMessage;
import com.flashnote.favorite.mapper.FavoriteMessageMapper;
import com.flashnote.favorite.service.FavoriteService;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FavoriteServiceImpl implements FavoriteService {
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final FavoriteMessageMapper favoriteMessageMapper;
    private final CurrentUserService currentUserService;

    public FavoriteServiceImpl(UserMapper userMapper,
                               MessageMapper messageMapper,
                               FlashNoteMapper flashNoteMapper,
                               FavoriteMessageMapper favoriteMessageMapper,
                               CurrentUserService currentUserService) {
        this.userMapper = userMapper;
        this.messageMapper = messageMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.favoriteMessageMapper = favoriteMessageMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    public List<FavoriteMessageItem> listFavorites(String username) {
        Long userId = currentUserService.getRequiredUserId(username);
        List<FavoriteMessage> favorites = favoriteMessageMapper.selectList(new LambdaQueryWrapper<FavoriteMessage>()
                .eq(FavoriteMessage::getUserId, userId)
                .orderByDesc(FavoriteMessage::getCreatedAt));
        List<FavoriteMessageItem> items = new ArrayList<>();
        for (FavoriteMessage favorite : favorites) {
            Message message = messageMapper.selectById(favorite.getMessageId());
            if (message == null || !belongsToUser(message, userId)) {
                continue;
            }
            items.add(toItem(favorite, message, userId));
        }
        return items;
    }

    @Override
    public FavoriteMessageItem addFavorite(String username, Long messageId) {
        Long userId = currentUserService.getRequiredUserId(username);
        Message message = getAccessibleMessage(userId, messageId);

        FavoriteMessage favorite = favoriteMessageMapper.selectOne(new LambdaQueryWrapper<FavoriteMessage>()
                .eq(FavoriteMessage::getUserId, userId)
                .eq(FavoriteMessage::getMessageId, messageId));
        if (favorite == null) {
            favorite = new FavoriteMessage();
            favorite.setUserId(userId);
            favorite.setMessageId(messageId);
            try {
                favoriteMessageMapper.insert(favorite);
            } catch (DuplicateKeyException e) {
                log.debug("Favorite already exists for userId={}, messageId={}", userId, messageId);
                favorite = favoriteMessageMapper.selectOne(new LambdaQueryWrapper<FavoriteMessage>()
                        .eq(FavoriteMessage::getUserId, userId)
                        .eq(FavoriteMessage::getMessageId, messageId));
            }
        }
        return toItem(favorite, message, userId);
    }

    @Override
    public void removeFavorite(String username, Long messageId) {
        Long userId = currentUserService.getRequiredUserId(username);
        favoriteMessageMapper.delete(new LambdaQueryWrapper<FavoriteMessage>()
                .eq(FavoriteMessage::getUserId, userId)
                .eq(FavoriteMessage::getMessageId, messageId));
    }

    private FavoriteMessageItem toItem(FavoriteMessage favorite, Message message, Long userId) {
        FavoriteMessageItem item = new FavoriteMessageItem();
        item.setId(favorite.getId());
        item.setMessageId(message.getId());
        item.setFlashNoteId(message.getFlashNoteId());
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setMessageCreatedAt(message.getCreatedAt());
        item.setFavoritedAt(favorite.getCreatedAt());
        item.setMediaType(message.getMediaType());
        item.setMediaUrl(message.getMediaUrl());
        item.setFileName(message.getFileName());
        item.setFileSize(message.getFileSize());
        item.setMediaDuration(message.getMediaDuration());
        item.setPayload(message.getPayload());
        if (message.getFlashNoteId() != null) {
            if (message.getFlashNoteId() == NoteConstants.COLLECTION_BOX_NOTE_ID) {
                item.setFlashNoteTitle(NoteConstants.COLLECTION_BOX_TITLE);
                item.setFlashNoteIcon(NoteConstants.COLLECTION_BOX_ICON);
                return item;
            }
            FlashNote flashNote = flashNoteMapper.selectById(message.getFlashNoteId());
            if (flashNote != null && userId.equals(flashNote.getUserId())) {
                item.setFlashNoteTitle(flashNote.getTitle());
                item.setFlashNoteIcon(flashNote.getIcon());
            }
        }
        return item;
    }

    private Message getAccessibleMessage(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null || !belongsToUser(message, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private boolean belongsToUser(Message message, Long userId) {
        return userId.equals(message.getSenderId()) || userId.equals(message.getReceiverId());
    }
}
