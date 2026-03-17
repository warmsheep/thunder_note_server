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
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class FlashNoteServiceImpl implements FlashNoteService {
    private final UserMapper userMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final MessageMapper messageMapper;

    public FlashNoteServiceImpl(UserMapper userMapper, FlashNoteMapper flashNoteMapper, MessageMapper messageMapper) {
        this.userMapper = userMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<FlashNote> listNotes(String username) {
        Long userId = getRequiredUserId(username);
        return flashNoteMapper.selectList(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false)
                .orderByDesc(FlashNote::getUpdatedAt));
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
        flashNoteMapper.insert(note);
        return note;
    }

    @Override
    public FlashNote updateNote(String username, Long noteId, FlashNote incoming) {
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
        flashNoteMapper.updateById(note);
        return note;
    }

    @Override
    public void deleteNote(String username, Long noteId) {
        Long userId = getRequiredUserId(username);
        FlashNote note = flashNoteMapper.selectOne(new LambdaQueryWrapper<FlashNote>()
                .eq(FlashNote::getId, noteId)
                .eq(FlashNote::getUserId, userId)
                .eq(FlashNote::getDeleted, false));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }
        note.setDeleted(true);
        flashNoteMapper.updateById(note);
    }

    private Long getRequiredUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user.getId();
    }
}
