package com.flashnote.flashnote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.constant.NoteConstants;
import com.flashnote.common.constant.MediaType;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.flashnote.dto.FlashNoteCreateRequest;
import com.flashnote.flashnote.dto.FlashNoteSearchResponse;
import com.flashnote.flashnote.dto.FlashNoteSearchResult;
import com.flashnote.flashnote.dto.FlashNoteUpdateRequest;
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
    private final UserMapper userMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final MessageMapper messageMapper;
    private final FileService fileService;
    private final CurrentUserService currentUserService;

    @Autowired
    public FlashNoteServiceImpl(UserMapper userMapper,
                                FlashNoteMapper flashNoteMapper,
                                MessageMapper messageMapper,
                                FileService fileService,
                                CurrentUserService currentUserService) {
        this.userMapper = userMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.messageMapper = messageMapper;
        this.fileService = fileService;
        this.currentUserService = currentUserService;
    }

    public FlashNoteServiceImpl(UserMapper userMapper,
                                FlashNoteMapper flashNoteMapper,
                                MessageMapper messageMapper) {
        this(userMapper, flashNoteMapper, messageMapper, null, null);
    }

    @Override
    public List<FlashNote> listNotes(String username) {
        Long userId = currentUserService.getRequiredUserId(username);
        List<FlashNote> queried = flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false)
                .orderByDesc(FlashNote::getPinned)
                .orderByDesc(FlashNote::getUpdatedAt));
        List<FlashNote> notes = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
        FlashNote collectionBox = buildCollectionBoxNote(userId);
        notes.removeIf(note -> note.getId() != null && note.getId().equals(NoteConstants.COLLECTION_BOX_NOTE_ID));
        notes.add(0, collectionBox);
        fillLatestMessages(notes, userId);
        return notes;
    }

    @Override
    public FlashNoteSearchResponse searchNotes(String username, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            List<FlashNote> notes = listNotes(username);
            List<FlashNoteSearchResult> results = notes.stream()
                    .map(note -> new FlashNoteSearchResult(note, null, true))
                    .collect(Collectors.toList());
            return new FlashNoteSearchResponse(results, new ArrayList<>());
        }
        Long userId = currentUserService.getRequiredUserId(username);

        List<FlashNote> titleMatchedNotes = flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false)
                .and(wrapper -> wrapper.like(FlashNote::getTitle, normalized)
                        .or()
                        .like(FlashNote::getContent, normalized))
                .orderByDesc(FlashNote::getUpdatedAt));
        Set<Long> titleMatchedIds = titleMatchedNotes.stream()
                .map(FlashNote::getId)
                .collect(Collectors.toSet());

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
        List<FlashNote> messageMatchedOnlyNotes = new ArrayList<>();
        if (!messageMatchedNoteIds.isEmpty()) {
            List<FlashNote> allMessageMatchedNotes = flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                    .eq(FlashNote::getUserId, userId)
                    .eq(FlashNote::getDeleted, false)
                    .in(FlashNote::getId, messageMatchedNoteIds)
                    .orderByDesc(FlashNote::getUpdatedAt));
            for (FlashNote note : allMessageMatchedNotes) {
                if (!titleMatchedIds.contains(note.getId())) {
                    messageMatchedOnlyNotes.add(note);
                }
            }
        }

        List<FlashNoteSearchResult> noteNameResults = titleMatchedNotes.stream()
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
                    return new FlashNoteSearchResult(note, matchedMessageInfos, true);
                })
                .collect(Collectors.toList());

        List<FlashNoteSearchResult> messageContentResults = messageMatchedOnlyNotes.stream()
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
                    return new FlashNoteSearchResult(note, matchedMessageInfos, false);
                })
                .collect(Collectors.toList());

        return new FlashNoteSearchResponse(noteNameResults, messageContentResults);
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
        Page<Message> beforePage = new Page<>(1, 3);
        messageMapper.selectPage(beforePage, new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, flashNoteId)
                .lt(Message::getId, messageId)
                .orderByDesc(Message::getId));
        List<Message> before = beforePage.getRecords();
        Collections.reverse(before);

        // 查自身
        Message self = messageMapper.selectById(messageId);

        // 查后3条: id > messageId, orderByAsc, limit 3
        Page<Message> afterPage = new Page<>(1, 3);
        messageMapper.selectPage(afterPage, new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, flashNoteId)
                .gt(Message::getId, messageId)
                .orderByAsc(Message::getId));
        List<Message> after = afterPage.getRecords();

        List<Message> context = new ArrayList<>(before);
        if (self != null) context.add(self);
        context.addAll(after);
        return context;
    }

    @Override
    public FlashNote createNote(String username, FlashNoteCreateRequest request) {
        Long userId = currentUserService.getRequiredUserId(username);
        FlashNote note = new FlashNote();
        note.setUserId(userId);
        note.setTitle(request.getTitle());
        note.setIcon(request.getIcon());
        note.setContent(request.getContent());
        note.setTags(request.getTags());
        note.setDeleted(false);
        note.setInbox(false);
        note.setPinned(Boolean.TRUE.equals(request.getPinned()));
        note.setHidden(Boolean.TRUE.equals(request.getHidden()));
        flashNoteMapper.insert(note);
        return note;
    }

    @Override
    public FlashNote updateNote(String username, Long noteId, FlashNoteUpdateRequest request) {
        if (noteId != null && noteId == NoteConstants.COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be edited");
        }
        Long userId = currentUserService.getRequiredUserId(username);
        FlashNote note = flashNoteMapper.selectOne(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getId, noteId)
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false));

        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }

        if (request.getTitle() != null) {
            note.setTitle(request.getTitle());
        }
        if (request.getIcon() != null) {
            note.setIcon(request.getIcon());
        }
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }
        if (request.getTags() != null) {
            note.setTags(request.getTags());
        }
        if (request.getPinned() != null) {
            note.setPinned(request.getPinned());
        }
        if (request.getHidden() != null) {
            note.setHidden(request.getHidden());
        }
        flashNoteMapper.updateById(note);
        return note;
    }

    @Override
    public FlashNote setPinned(String username, Long noteId, boolean pinned) {
        if (noteId != null && noteId == NoteConstants.COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be unpinned");
        }
        Long userId = currentUserService.getRequiredUserId(username);
        FlashNote note = getOwnedNote(userId, noteId);
        note.setPinned(pinned);
        flashNoteMapper.updateById(note);
        return note;
    }

    @Override
    public FlashNote setHidden(String username, Long noteId, boolean hidden) {
        if (noteId != null && noteId == NoteConstants.COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be hidden");
        }
        Long userId = currentUserService.getRequiredUserId(username);
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
        if (noteId != null && noteId == NoteConstants.COLLECTION_BOX_NOTE_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection box cannot be deleted");
        }
        Long userId = currentUserService.getRequiredUserId(username);
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

    private void fillLatestMessages(List<FlashNote> notes, Long userId) {
        if (notes == null || notes.isEmpty()) {
            return;
        }

        List<Long> noteIds = notes.stream()
                .map(FlashNote::getId)
                .filter(id -> id != null)
                .filter(id -> id != NoteConstants.COLLECTION_BOX_NOTE_ID)
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
            if (note.getId() != null && note.getId() == NoteConstants.COLLECTION_BOX_NOTE_ID) {
                note.setLatestMessage(resolveLatestMessage(inboxLatest));
            } else {
                Message latest = latestByNoteId.get(note.getId());
                note.setLatestMessage(resolveLatestMessage(latest));
            }
        }
    }

    private Message latestInboxMessage(Long userId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, NoteConstants.COLLECTION_BOX_NOTE_ID)
                .eq(Message::getSenderId, userId)
                .eq(Message::getReceiverId, userId)
                .orderByDesc(Message::getCreatedAt, Message::getId)
                .last("LIMIT 1"));
    }

    private FlashNote buildCollectionBoxNote(Long userId) {
        FlashNote note = new FlashNote();
        note.setId(NoteConstants.COLLECTION_BOX_NOTE_ID);
        note.setUserId(userId);
        note.setTitle(NoteConstants.COLLECTION_BOX_TITLE);
        note.setIcon(NoteConstants.COLLECTION_BOX_ICON);
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
        return MediaType.resolveDisplay(latest.getMediaType(), latest.getContent());
    }
}
