package com.flashnote.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.user.dto.ContactSearchUserDto;
import com.flashnote.user.dto.ContactUserDto;
import com.flashnote.user.dto.FriendRequestDto;
import com.flashnote.user.entity.FriendRelation;
import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.mapper.FriendRelationMapper;
import com.flashnote.user.mapper.UserProfileMapper;
import com.flashnote.user.service.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {
    private static final String REL_PENDING = "PENDING";
    private static final String REL_ACCEPTED = "ACCEPTED";
    private static final String REL_REJECTED = "REJECTED";

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final FriendRelationMapper friendRelationMapper;
    private final MessageMapper messageMapper;

    public UserServiceImpl(UserMapper userMapper,
                           UserProfileMapper userProfileMapper,
                           FriendRelationMapper friendRelationMapper,
                           MessageMapper messageMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.friendRelationMapper = friendRelationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public UserProfile getProfile(String username) {
        User user = getRequiredUser(username);
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, user.getId()));

        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(user.getId());
            profileMapperInsert(profile);
        }
        profile.setAvatar(user.getAvatar());
        profile.setNickname(user.getNickname());
        return profile;
    }

    @Override
    public UserProfile updateProfile(String username, UserProfile incoming) {
        UserProfile current = getProfile(username);
        current.setBio(incoming.getBio());
        current.setPreferencesJson(incoming.getPreferencesJson());
        userProfileMapper.updateById(current);
        User user = getRequiredUser(username);
        if (incoming.getNickname() != null) {
            user.setNickname(incoming.getNickname().trim());
            current.setNickname(user.getNickname());
        }
        if (incoming.getAvatar() != null) {
            user.setAvatar(incoming.getAvatar().trim());
            current.setAvatar(user.getAvatar());
        }
        userMapper.updateById(user);
        return current;
    }

    @Override
    public String updateAvatar(String username, String avatarUrl) {
        User user = getRequiredUser(username);
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return avatarUrl;
    }

    @Override
    public List<ContactUserDto> listContacts(String username) {
        User currentUser = getRequiredUser(username);
        List<FriendRelation> accepted = friendRelationMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .and(wrapper -> wrapper
                        .eq(FriendRelation::getStatus, REL_ACCEPTED)
                        .and(inner -> inner.eq(FriendRelation::getRequesterId, currentUser.getId())
                                .or()
                                .eq(FriendRelation::getAddresseeId, currentUser.getId()))
                        .or()
                        .eq(FriendRelation::getStatus, REL_PENDING)
                        .eq(FriendRelation::getRequesterId, currentUser.getId()))
                .orderByDesc(FriendRelation::getUpdatedAt)
                .orderByDesc(FriendRelation::getId));
        Map<Long, String> relationStatusByUserId = new HashMap<>();
        Set<Long> friendIds = new HashSet<>();
        for (FriendRelation relation : accepted) {
            Long otherId = relation.getRequesterId().equals(currentUser.getId())
                    ? relation.getAddresseeId()
                    : relation.getRequesterId();
            if (otherId != null) {
                friendIds.add(otherId);
                relationStatusByUserId.put(otherId, REL_ACCEPTED.equals(relation.getStatus()) ? "FRIEND" : "PENDING_SENT");
            }
        }
        if (friendIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> latestMessageByUserId = new HashMap<>();
        for (Long friendId : friendIds) {
            latestMessageByUserId.put(friendId, findLatestConversationMessage(currentUser.getId(), friendId));
        }
        return userMapper.selectBatchIds(friendIds).stream()
                .filter(user -> user != null && user.getStatus() != null && user.getStatus() == 1)
                .sorted((a, b) -> compareContactUsers(a, b, relationStatusByUserId))
                .map(user -> new ContactUserDto(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getAvatar(),
                        relationStatusByUserId.getOrDefault(user.getId(), "FRIEND"),
                        latestMessageByUserId.get(user.getId())))
                .toList();
    }

    private int compareContactUsers(User left, User right, Map<Long, String> relationStatusByUserId) {
        int statusCompare = Integer.compare(
                rankRelationStatus(relationStatusByUserId.getOrDefault(left.getId(), "FRIEND")),
                rankRelationStatus(relationStatusByUserId.getOrDefault(right.getId(), "FRIEND")));
        if (statusCompare != 0) {
            return statusCompare;
        }
        return left.getUsername().compareToIgnoreCase(right.getUsername());
    }

    private int rankRelationStatus(String relationStatus) {
        if ("FRIEND".equals(relationStatus)) {
            return 0;
        }
        if ("PENDING_SENT".equals(relationStatus)) {
            return 1;
        }
        return 2;
    }

    private String findLatestConversationMessage(Long currentUserId, Long otherUserId) {
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .and(pair -> pair.eq(Message::getSenderId, currentUserId)
                                .eq(Message::getReceiverId, otherUserId))
                        .or(pair -> pair.eq(Message::getSenderId, otherUserId)
                                .eq(Message::getReceiverId, currentUserId)))
                .orderByDesc(Message::getCreatedAt)
                .orderByDesc(Message::getId)
                .last("LIMIT 1"));
        if (messages.isEmpty()) {
            return null;
        }
        return resolveLatestMessage(messages.get(0));
    }

    private String resolveLatestMessage(Message latest) {
        if (latest == null) {
            return null;
        }
        String mediaType = latest.getMediaType();
        if (mediaType != null && !mediaType.isBlank() && !"TEXT".equalsIgnoreCase(mediaType)) {
            if ("IMAGE".equalsIgnoreCase(mediaType)) {
                return "[图片]";
            }
            if ("VIDEO".equalsIgnoreCase(mediaType)) {
                return "[视频]";
            }
            if ("VOICE".equalsIgnoreCase(mediaType)) {
                return "[语音]";
            }
            return "[文件]";
        }
        return latest.getContent();
    }

    @Override
    public List<FriendRequestDto> listPendingRequests(String username) {
        User currentUser = getRequiredUser(username);
        List<FriendRelation> pending = friendRelationMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getStatus, REL_PENDING)
                .eq(FriendRelation::getAddresseeId, currentUser.getId())
                .orderByDesc(FriendRelation::getCreatedAt));
        if (pending.isEmpty()) {
            return List.of();
        }
        Set<Long> requesterIds = new HashSet<>();
        for (FriendRelation relation : pending) {
            requesterIds.add(relation.getRequesterId());
        }
        Map<Long, User> users = new HashMap<>();
        for (User user : userMapper.selectBatchIds(requesterIds)) {
            if (user != null) {
                users.put(user.getId(), user);
            }
        }

        List<FriendRequestDto> result = new ArrayList<>();
        for (FriendRelation relation : pending) {
            User user = users.get(relation.getRequesterId());
            if (user == null) {
                continue;
            }
            result.add(new FriendRequestDto(relation.getId(), user.getId(), user.getUsername(), user.getNickname(), user.getAvatar()));
        }
        return result;
    }

    @Override
    public Long countPendingRequests(String username) {
        User currentUser = getRequiredUser(username);
        return friendRelationMapper.selectCount(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getStatus, REL_PENDING)
                .eq(FriendRelation::getAddresseeId, currentUser.getId()));
    }

    @Override
    public void sendFriendRequest(String username, Long targetUserId) {
        User currentUser = getRequiredUser(username);
        if (targetUserId == null || targetUserId <= 0L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target user is required");
        }
        if (currentUser.getId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot add yourself");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null || target.getStatus() == null || target.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Target user not found");
        }

        FriendRelation pair = findPair(currentUser.getId(), targetUserId);
        if (pair != null) {
            if (REL_ACCEPTED.equals(pair.getStatus())) {
                return;
            }
            if (REL_PENDING.equals(pair.getStatus()) && pair.getRequesterId().equals(currentUser.getId())) {
                return;
            }
            pair.setRequesterId(currentUser.getId());
            pair.setAddresseeId(targetUserId);
            pair.setStatus(REL_PENDING);
            pair.setHandledAt(null);
            friendRelationMapper.updateById(pair);
            return;
        }

        FriendRelation relation = new FriendRelation();
        relation.setRequesterId(currentUser.getId());
        relation.setAddresseeId(targetUserId);
        relation.setStatus(REL_PENDING);
        relation.setHandledAt(null);
        friendRelationMapper.insert(relation);
    }

    @Override
    public void acceptFriendRequest(String username, Long requestId) {
        User currentUser = getRequiredUser(username);
        FriendRelation relation = getPendingRequestOwnedByCurrentUser(currentUser.getId(), requestId);
        relation.setStatus(REL_ACCEPTED);
        relation.setHandledAt(LocalDateTime.now());
        friendRelationMapper.updateById(relation);
    }

    @Override
    public void rejectFriendRequest(String username, Long requestId) {
        User currentUser = getRequiredUser(username);
        FriendRelation relation = getPendingRequestOwnedByCurrentUser(currentUser.getId(), requestId);
        relation.setStatus(REL_REJECTED);
        relation.setHandledAt(LocalDateTime.now());
        friendRelationMapper.updateById(relation);
    }

    @Override
    public void removeContact(String username, Long contactUserId) {
        User currentUser = getRequiredUser(username);
        if (contactUserId == null || contactUserId <= 0L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contact user is required");
        }
        FriendRelation pair = findPair(currentUser.getId(), contactUserId);
        if (pair == null || !REL_ACCEPTED.equals(pair.getStatus())) {
            return;
        }
        friendRelationMapper.deleteById(pair.getId());
    }

    @Override
    public List<ContactSearchUserDto> searchUsers(String username, String keyword) {
        User currentUser = getRequiredUser(username);
        String normalized = keyword == null ? "" : keyword.trim();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .ne(User::getId, currentUser.getId())
                .and(wrapper -> wrapper.like(User::getUsername, normalized)
                        .or()
                        .like(User::getNickname, normalized))
                .orderByAsc(User::getUsername)
                .last("LIMIT 30"));

        List<ContactSearchUserDto> result = new ArrayList<>();
        for (User user : users) {
            String relationStatus = resolveRelationStatus(currentUser.getId(), user.getId());
            result.add(new ContactSearchUserDto(
                    user.getId(),
                    user.getUsername(),
                    user.getNickname(),
                    user.getAvatar(),
                    relationStatus
            ));
        }
        return result;
    }

    private User getRequiredUser(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user;
    }

    private FriendRelation getPendingRequestOwnedByCurrentUser(Long currentUserId, Long requestId) {
        if (requestId == null || requestId <= 0L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request id is required");
        }
        FriendRelation relation = friendRelationMapper.selectById(requestId);
        if (relation == null || !REL_PENDING.equals(relation.getStatus()) || !currentUserId.equals(relation.getAddresseeId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Pending request not found");
        }
        return relation;
    }

    private FriendRelation findPair(Long userA, Long userB) {
        return friendRelationMapper.selectOne(new LambdaQueryWrapper<FriendRelation>()
                .and(wrapper -> wrapper
                        .and(pair -> pair.eq(FriendRelation::getRequesterId, userA).eq(FriendRelation::getAddresseeId, userB))
                        .or(pair -> pair.eq(FriendRelation::getRequesterId, userB).eq(FriendRelation::getAddresseeId, userA)))
                .last("LIMIT 1"));
    }

    private String resolveRelationStatus(Long currentUserId, Long otherUserId) {
        FriendRelation relation = findPair(currentUserId, otherUserId);
        if (relation == null) {
            return "NONE";
        }
        if (REL_ACCEPTED.equals(relation.getStatus())) {
            return "FRIEND";
        }
        if (REL_PENDING.equals(relation.getStatus())) {
            return relation.getRequesterId().equals(currentUserId) ? "PENDING_SENT" : "PENDING_RECEIVED";
        }
        return "NONE";
    }

    private void profileMapperInsert(UserProfile profile) {
        userProfileMapper.insert(profile);
    }
}
