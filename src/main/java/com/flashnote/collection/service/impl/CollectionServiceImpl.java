package com.flashnote.collection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.collection.entity.Collection;
import com.flashnote.collection.mapper.CollectionMapper;
import com.flashnote.collection.service.CollectionService;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollectionServiceImpl implements CollectionService {
    private final UserMapper userMapper;
    private final CollectionMapper collectionMapper;

    public CollectionServiceImpl(UserMapper userMapper, CollectionMapper collectionMapper) {
        this.userMapper = userMapper;
        this.collectionMapper = collectionMapper;
    }

    @Override
    public List<Collection> listCollections(String username) {
        Long userId = getRequiredUserId(username);
        return collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getUserId, userId)
                .orderByDesc(Collection::getUpdatedAt));
    }

    @Override
    public Collection createCollection(String username, Collection collection) {
        Long userId = getRequiredUserId(username);
        collection.setUserId(userId);
        collectionMapper.insert(collection);
        return collection;
    }

    @Override
    public Collection updateCollection(String username, Long id, Collection incoming) {
        Long userId = getRequiredUserId(username);
        Collection collection = collectionMapper.selectOne(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getId, id)
                .eq(Collection::getUserId, userId));

        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collection not found");
        }

        collection.setName(incoming.getName());
        collection.setDescription(incoming.getDescription());
        collectionMapper.updateById(collection);
        return collection;
    }

    @Override
    public void deleteCollection(String username, Long id) {
        Long userId = getRequiredUserId(username);
        collectionMapper.delete(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getId, id)
                .eq(Collection::getUserId, userId));
    }

    private Long getRequiredUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user.getId();
    }
}
