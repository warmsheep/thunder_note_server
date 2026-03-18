package com.flashnote.flashnote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.flashnote.dto.FlashNoteSearchResult;
import com.flashnote.flashnote.dto.MatchedMessageInfo;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.flashnote.service.FlashNoteService;
import com.flashnote.file.service.FileService;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FlashNoteServiceImpl implements FlashNoteService {
    public static final long COLLECTION_BOX_NOTE_ID = -1L;
    private static final String COLLECTION_BOX_TITLE = "收集箱";
    private static final String COLLECTION_BOX_ICON = "📥";

    private final UserMapper userMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final MessageMapper messageMapper;
    private final FileService fileService;

    @Autowired
    public FlashNoteServiceImpl(UserMapper userMapper,
                                FlashNoteMapper flashNoteMapper,
                                MessageMapper messageMapper,
                                FileService fileService) {
        this.userMapper = userMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.messageMapper = messageMapper;
        this.fileService = fileService;
    }

    public FlashNoteServiceImpl(UserMapper userMapper,
                                FlashNoteMapper flashNoteMapper,
                                MessageMapper messageMapper) {
        this(userMapper, flashNoteMapper, messageMapper, null);
    }

    @Override
    public List<FlashNote> listNotes(String username) {
        Long userId = getRequiredUserId(username);
        List<FlashNote> queried = flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false)
                .orderByDesc(FlashNote::getPinned)
                .orderByDesc(FlashNote::getUpdatedAt));
        List<FlashNote> notes = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
        FlashNote collectionBox = buildCollectionBoxNote(userId);
        notes.removeIf(note -> note.getId() != null && note.getId().equals(COLLECTION_BOX_NOTE_ID));
        notes.add(0, collectionBox);
        fillLatestMessages(notes, userId);
        return notes;
    }

    @Override
    public List<FlashNoteSearchResult> searchNotes(String username, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            List<FlashNote> notes = listNotes(username);
            return notes.stream()
                    .map(note -> new FlashNoteSearchResult(note, null))
                    .collect(Collectors.toList());
        }
        Long userId = getRequiredUserId(username);

        List<FlashNote> titleMatchedNotes = flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false)
                .and(wrapper -> wrapper.like(FlashNote::getTitle, normalized)
                        .or()
                        .like(FlashNote::getContent, normalized))
                .orderByDesc(FlashNote::getUpdatedAt));

        List<Message> matchedMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .like(Message::getContent, normalized)
                .and(wrapper -> wrapper.eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .isNotNull(Message::getFlashNoteId)
                .orderByDesc(Message::getCreatedAt));

        Map<Long, List<Message>> messagesByNoteId = matchedMessages.stream()
                .filter(m -> m.getFlashNoteId() != null)
                .collect(Collectors.groupingBy(Message::getFlashNoteId));

        Set<Long> messageMatchedNoteIds = messagesByNoteId.keySet();
        List<FlashNote> messageMatchedNotes = new ArrayList<>();
        if (!messageMatchedNoteIds.isEmpty()) {
            messageMatchedNotes = flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                    .eq(FlashNote::getUserId, userId)
                    .eq(FlashNote::getDeleted, false)
                    .in(FlashNote::getId, messageMatchedNoteIds)
                    .orderByDesc(FlashNote::getUpdatedAt));
        }

        Map<Long, FlashNote> mergedNotes = new LinkedHashMap<>();
        for (FlashNote note : titleMatchedNotes) {
            mergedNotes.put(note.getId(), note);
        }
        for (FlashNote note : messageMatchedNotes) {
            if (!mergedNotes.containsKey(note.getId())) {
                mergedNotes.put(note.getId(), note);
            }
        }

        List<FlashNote> orderedNotes = mergedNotes.values().stream()
                .sorted(Comparator.comparing(FlashNote::getUpdatedAt).reversed())
                .collect(Collectors.toList());

        return orderedNotes.stream()
                .map(note -> {
                    List<Message> noteMessages = messagesByNoteId.get(note.getId());
                    List<MatchedMessageInfo> matchedMessageInfos = null;
                    if (noteMessages != null && !noteMessages.isEmpty()) {
                        matchedMessageInfos = noteMessages.stream()
                                .map(msg -> {
                                    MatchedMessageInfo info = new MatchedMessageInfo(
                                            msg.getId(),
                                            generateSnippet(msg.getContent(), normalized));
                                    info.setContextMessages(getMessageContext(note.getId(), msg.getId()));
                                    return info;
                                })
                                .collect(Collectors.toList());
                    }
                    return new FlashNoteSearchResult(note, matchedMessageInfos);
                })
                .collect(Collectors.toList());
    }

    private String generateSnippet(String content, String query) {
        if (content == null || query == null) {
            return "";
        }
        String lowerContent = content.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int index = lowerContent.indexOf(lowerQuery);
        if (index == -1) {
            return content.length() > 50 ? content.substring(0, 50) + "..." : content;
        }

        int snippetLength = 50;
        int halfLength = snippetLength / 2;

        int start = Math.max(0, index - halfLength);
        int end = Math.min(content.length(), index + query.length() + halfLength);

        while (start > 0 && !Character.isWhitespace(content.charAt(start - 1))) {
            start--;
        }
        while (end < content.length() && !Character.isWhitespace(content.charAt(end))) {
            end++;
        }

        StringBuilder snippet = new StringBuilder();
        if (start > 0) {
            snippet.append("...");
        }
        snippet.append(content, start, end);
        if (end < content.length()) {
            snippet.append("...");
        }

        return snippet.toString();
    }

    private List<Message> getMessageContext(Long flashNoteId, Long messageId) {
        // 查前3条: id < messageId, orderByDesc, limit 3, 然后reverse
        List<Message> before = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, flashNoteId)
                .lt(Message::getId, messageId)
                .orderByDesc(Message::getId)
                .last("LIMIT 3"));
        Collections.reverse(before);

        // 查自身
        Message self = messageMapper.selectById(messageId);

        // 查后3条: id > messageId, orderByAsc, limit 3
        List<Message> after = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, flashNoteId)
                .gt(Message::getId, messageId)
                .orderByAsc(Message::getId)
                .last("LIMIT 3"));

        List<Message> context = new ArrayList<>(before);
        if (self != null) context.add(self);
        context.addAll(after);
        return context;
    }

    @Override
    public FlashNote createNote(String username, FlashNote note) {
        Long userId = getRequiredUserId(username);
        note.setUserId(userId);
        note.setDeleted(false);
        note.setInbox(false);
        note.setPinned(Boolean.TRUE.equals(note.getPinned()));
        note.setHidden(Boolean.TRUE.equals(note.getHidden()));
        flashNoteMapper.insert(note);
        return note;
    }

    @Override
    public FlashNote updateNote(String username, Long noteId, FlashNote incoming) {
        if (noteId != null && noteId == COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be edited");
        }
        Long userId = getRequiredUserId(username);
        FlashNote note = flashNoteMapper.selectOne(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getId, noteId)
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false));

        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }

        note.setTitle(incoming.getTitle());
        note.setIcon(incoming.getIcon());
        note.setContent(incoming.getContent());
        note.setTags(incoming.getTags());
        if (incoming.getPinned() != null) {
            note.setPinned(incoming.getPinned());
        }
        if (incoming.getHidden() != null) {
            note.setHidden(incoming.getHidden());
        }
        flashNoteMapper.updateById(note);
        return note;
    }

    @Override
    public FlashNote setPinned(String username, Long noteId, boolean pinned) {
        if (noteId != null && noteId == COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be unpinned");
        }
        Long userId = getRequiredUserId(username);
        FlashNote note = getOwnedNote(userId, noteId);
        note.setPinned(pinned);
        flashNoteMapper.updateById(note);
        return note;
    }

    @Override
    public FlashNote setHidden(String username, Long noteId, boolean hidden) {
        if (noteId != null && noteId == COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be hidden");
        }
        Long userId = getRequiredUserId(username);
        FlashNote note = getOwnedNote(userId, noteId);
        note.setHidden(hidden);
        if (hidden) {
            note.setPinned(false);
        }
        flashNoteMapper.updateById(note);
        return note;
    }

    @Override
    public void deleteNote(String username, Long noteId) {
        if (noteId != null && noteId == COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be deleted");
        }
        Long userId = getRequiredUserId(username);
        FlashNote note = getOwnedNote(userId, noteId);
        note.setDeleted(true);
        flashNoteMapper.updateById(note);

        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, noteId));
        for (Message message : messages) {
            if (fileService != null && message.getMediaUrl() != null && !message.getMediaUrl().isBlank()) {
                fileService.deleteObject(message.getMediaUrl());
            }
            if (fileService != null && message.getThumbnailUrl() != null && !message.getThumbnailUrl().isBlank()) {
                fileService.deleteObject(message.getThumbnailUrl());
            }
        }

        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, noteId));
    }

    private Long getRequiredUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user.getId();
    }

    private void fillLatestMessages(List<FlashNote> notes, Long userId) {
        if (notes == null || notes.isEmpty()) {
            return;
        }

        List<Long> noteIds = notes.stream()
                .map(FlashNote::getId)
                .filter(id -> id != null)
                .filter(id -> id != COLLECTION_BOX_NOTE_ID)
                .collect(Collectors.toList());
        Message inboxLatest = latestInboxMessage(userId);

        List<Message> allMessages = noteIds.isEmpty()
                ? Collections.emptyList()
                : messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .in(Message::getFlashNoteId, noteIds)
                .and(wrapper -> wrapper.eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .orderByDesc(Message::getCreatedAt)
                .orderByDesc(Message::getId));

        Map<Long, Message> latestByNoteId = new LinkedHashMap<>();
        for (Message message : allMessages) {
            if (message.getFlashNoteId() != null && !latestByNoteId.containsKey(message.getFlashNoteId())) {
                latestByNoteId.put(message.getFlashNoteId(), message);
            }
        }

        for (FlashNote note : notes) {
            if (note.getId() != null && note.getId() == COLLECTION_BOX_NOTE_ID) {
                note.setLatestMessage(resolveLatestMessage(inboxLatest));
            } else {
                Message latest = latestByNoteId.get(note.getId());
                note.setLatestMessage(resolveLatestMessage(latest));
            }
        }
    }

    private Message latestInboxMessage(Long userId) {
        List<Message> inboxMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, COLLECTION_BOX_NOTE_ID)
                .and(wrapper -> wrapper.eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .orderByDesc(Message::getCreatedAt)
                .orderByDesc(Message::getId)
                .last("LIMIT 1"));
        return inboxMessages.isEmpty() ? null : inboxMessages.get(0);
    }

    private FlashNote buildCollectionBoxNote(Long userId) {
        FlashNote note = new FlashNote();
        note.setId(COLLECTION_BOX_NOTE_ID);
        note.setUserId(userId);
        note.setTitle(COLLECTION_BOX_TITLE);
        note.setIcon(COLLECTION_BOX_ICON);
        note.setContent("");
        note.setTags("收集箱");
        note.setDeleted(false);
        note.setPinned(true);
        note.setHidden(false);
        note.setInbox(true);
        LocalDateTime now = LocalDateTime.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        return note;
    }

    private FlashNote getOwnedNote(Long userId, Long noteId) {
        FlashNote note = flashNoteMapper.selectOne(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getId, noteId)
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }
        if (Boolean.TRUE.equals(note.getInbox())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be modified");
        }
        return note;
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
}
