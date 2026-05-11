package com.flashnote.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.constant.MediaType;
import com.flashnote.common.service.CurrentUserService;
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
    private final CurrentUserService currentUserService;

    public UserServiceImpl(UserMapper userMapper,
                           UserProfileMapper userProfileMapper,
                           FriendRelationMapper friendRelationMapper,
                           MessageMapper messageMapper,
                           CurrentUserService currentUserService) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.friendRelationMapper = friendRelationMapper;
        this.messageMapper = messageMapper;
        this.currentUserService = currentUserService;
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
        // D1-W28-17 归一化入库：服务端可能多域名 / 反代部署，存绝对 URL 会让 host 跨设备失效，
        // 所以入库前把绝对下载链接抽成 objectName，只存"相对资源标识"。
        // 允许形态（入库版本）：
        //   1. objectName（如 "1/abc.png"，含 '/' 的相对资源路径）
        //   2. emoji / 短字符串（≤ 16，无控制字符 / 引号 / 尖括号）
        //   3. 外链头像 URL（http/https 但不是 /api/files/download 形式 — 比如用户填 CDN）
        // 入参 avatarUrl 兼容历史调用：绝对下载 URL → 自动抽 objectName。
        if (avatarUrl == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar is required");
        }
        String trimmed = avatarUrl.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar is required");
        }
        String normalized = normalizeAvatar(trimmed);

        User user = getRequiredUser(username);
        user.setAvatar(normalized);
        userMapper.updateById(user);
        return normalized;
    }

    /**
     * 把传入的 avatar 字段归一化为最适合入库的形式：
     *   - 闪记内部下载 URL (`http(s)://*\/api/files/download?objectName=...`) → 抽出 objectName
     *   - 外链 URL → 原样保留（限长 ≤ 512 与表字段一致）
     *   - 纯 objectName / emoji → 字符白名单校验后保留
     * 抛 BusinessException 时表示 400 用户输入错误。
     */
    static String normalizeAvatar(String trimmed) {
        boolean isHttpUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://");
        if (isHttpUrl) {
            String objectName = extractObjectNameFromDownloadUrl(trimmed);
            if (objectName != null && !objectName.isEmpty()) {
                if (objectName.length() > 512) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar objectName too long");
                }
                return objectName;
            }
            // 外链头像（CDN / 第三方）保留原样
            if (trimmed.length() > 512) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar URL too long");
            }
            return trimmed;
        }
        // emoji / 短字符串 / objectName 共用一套白名单
        if (trimmed.length() > 512) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar is too long");
        }
        boolean hasSlash = trimmed.indexOf('/') >= 0;
        if (!hasSlash && trimmed.length() > 16) {
            // 非 objectName 形态当作 emoji / 短串，长度严格收紧避免被滥用为长文本
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar is too long");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == '"' || c == '\'' || c == '<' || c == '>') {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar contains invalid characters");
            }
        }
        return trimmed;
    }

    /**
     * 从形如 `http(s)://host[:port]/api/files/download?objectName=...` 的 URL 中抽出 objectName。
     * 失败 / 不是闪记下载链接时返回 null。
     */
    static String extractObjectNameFromDownloadUrl(String httpUrl) {
        try {
            java.net.URI uri = java.net.URI.create(httpUrl);
            if (!"/api/files/download".equals(uri.getPath())) {
                return null;
            }
            String query = uri.getRawQuery();
            if (query == null || query.isEmpty()) {
                return null;
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = pair.substring(0, eq);
                if ("objectName".equals(key)) {
                    return java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
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
        Message message = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .and(pair -> pair.eq(Message::getSenderId, currentUserId)
                                .eq(Message::getReceiverId, otherUserId))
                        .or(pair -> pair.eq(Message::getSenderId, otherUserId)
                                .eq(Message::getReceiverId, currentUserId)))
                .orderByDesc(Message::getCreatedAt, Message::getId)
                .last("LIMIT 1"));
        return message == null ? null : resolveLatestMessage(message);
    }

    private String resolveLatestMessage(Message latest) {
        if (latest == null) {
            return null;
        }
        return MediaType.resolveDisplay(latest.getMediaType(), latest.getContent());
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
    public void cancelFriendRequest(String username, Long requestId) {
        User currentUser = getRequiredUser(username);
        if (requestId == null || requestId <= 0L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request id is required");
        }
        FriendRelation relation = friendRelationMapper.selectById(requestId);
        if (relation == null || !REL_PENDING.equals(relation.getStatus()) || !currentUser.getId().equals(relation.getRequesterId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Pending request not found");
        }
        friendRelationMapper.deleteById(requestId);
    }

    @Override
    public void removeContact(String username, Long contactUserId) {
        User currentUser = getRequiredUser(username);
        if (contactUserId == null || contactUserId <= 0L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contact user is required");
        }
        FriendRelation pair = findPair(currentUser.getId(), contactUserId);
        if (pair == null) {
            return;
        }
        if (REL_ACCEPTED.equals(pair.getStatus()) || REL_PENDING.equals(pair.getStatus())) {
            friendRelationMapper.deleteById(pair.getId());
        }
    }

    @Override
    public List<ContactSearchUserDto> searchUsers(String username, String keyword) {
        User currentUser = getRequiredUser(username);
        String normalized = keyword == null ? "" : keyword.trim();
        Page<User> page = new Page<>(1, 30);
        userMapper.selectPage(page, new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .ne(User::getId, currentUser.getId())
                .and(wrapper -> wrapper.like(User::getUsername, normalized)
                        .or()
                        .like(User::getNickname, normalized))
                .orderByAsc(User::getUsername));
        List<User> users = page.getRecords();

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
        return currentUserService.getRequiredUser(username);
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
                        .or(pair -> pair.eq(FriendRelation::getRequesterId, userB).eq(FriendRelation::getAddresseeId, userA))));
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
