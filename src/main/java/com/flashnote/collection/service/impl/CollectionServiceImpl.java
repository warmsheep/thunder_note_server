package com.flashnote.collection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.collection.dto.CollectionCreateRequest;
import com.flashnote.collection.dto.CollectionUpdateRequest;
import com.flashnote.collection.entity.Collection;
import com.flashnote.collection.mapper.CollectionMapper;
import com.flashnote.collection.service.CollectionService;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CollectionServiceImpl implements CollectionService {
    private final UserMapper userMapper;
    private final CollectionMapper collectionMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final CurrentUserService currentUserService;

    public CollectionServiceImpl(UserMapper userMapper,
                                 CollectionMapper collectionMapper,
                                 FlashNoteMapper flashNoteMapper,
                                 CurrentUserService currentUserService) {
        this.userMapper = userMapper;
        this.collectionMapper = collectionMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    public List<Collection> listCollections(String username) {
        Long userId = currentUserService.getRequiredUserId(username);
        return collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getUserId, userId)
                .orderByDesc(Collection::getUpdatedAt));
    }

    @Override
    public Collection createCollection(String username, CollectionCreateRequest request) {
        Long userId = currentUserService.getRequiredUserId(username);
        Collection collection = new Collection();
        collection.setUserId(userId);
        collection.setName(request.getName());
        collection.setDescription(request.getDescription());
        collectionMapper.insert(collection);
        return collection;
    }

    @Override
    @Transactional
    public Collection updateCollection(String username, Long id, CollectionUpdateRequest request) {
        Long userId = currentUserService.getRequiredUserId(username);
        Collection collection = collectionMapper.selectOne(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getId, id)
                .eq(Collection::getUserId, userId));

        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collection not found");
        }

        String oldName = collection.getName();
        collection.setName(request.getName());
        collection.setDescription(request.getDescription());
        collectionMapper.updateById(collection);
        cascadeRename(userId, oldName, request.getName());
        return collection;
    }

    @Override
    @Transactional
    public void deleteCollection(String username, Long id) {
        Long userId = currentUserService.getRequiredUserId(username);
        Collection collection = collectionMapper.selectOne(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getId, id)
                .eq(Collection::getUserId, userId));

        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collection not found");
        }

        collectionMapper.delete(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getId, id)
                .eq(Collection::getUserId, userId));
        cascadeClear(userId, collection.getName());
    }

    private void cascadeRename(Long userId, String oldName, String newName) {
        if (isBlank(oldName) || oldName.equals(newName)) {
            return;
        }
        String normalizedOld = normalizeName(oldName);
        String normalizedNew = normalizeName(newName);
        flashNoteMapper.update(null, new UpdateWrapper<FlashNote>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .eq("tags", normalizedOld)
                .set("tags", normalizedNew));
    }

    private void cascadeClear(Long userId, String collectionName) {
        if (isBlank(collectionName)) {
            return;
        }
        String normalizedCollectionName = normalizeName(collectionName);
        flashNoteMapper.update(null, new UpdateWrapper<FlashNote>()
                .eq("user_id", userId)
                .eq("deleted", false)
                .eq("tags", normalizedCollectionName)
                .set("tags", null));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

}
