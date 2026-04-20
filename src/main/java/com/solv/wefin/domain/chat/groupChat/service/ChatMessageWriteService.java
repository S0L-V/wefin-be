package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ReplyMessageInfo;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import com.solv.wefin.domain.chat.groupChat.entity.MessageType;
import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageRepository;
import com.solv.wefin.domain.group.entity.Group;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageWriteService {

    private static final String SYSTEM = "시스템";

    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatMessageInfo publishUserMessage(User user, Group group, String content, ChatMessage replyTarget) {
        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.createUserMessage(user, group, content, replyTarget)
        );

        ChatMessageInfo info = toInfo(userMessage);
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(group.getId(), info));
        return info;
    }

    @Transactional
    public ChatMessageInfo publishSystemMessage(Group group, String content) {
        ChatMessage systemMessage = chatMessageRepository.save(
                ChatMessage.createSystemMessage(group, content)
        );

        ChatMessageInfo info = toInfo(systemMessage);
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(group.getId(), info));
        return info;
    }

    private ChatMessageInfo toInfo(ChatMessage message) {
        return new ChatMessageInfo(
                message.getId(),
                extractUserId(message),
                extractGroupId(message),
                message.getMessageType().name(),
                resolveSender(message),
                message.getContent(),
                message.getCreatedAt(),
                toReplyInfo(message.getReplyToMessage()),
                null,
                null
        );
    }

    private ReplyMessageInfo toReplyInfo(ChatMessage replyMessage) {
        if (replyMessage == null) {
            return null;
        }

        return new ReplyMessageInfo(
                replyMessage.getId(),
                resolveSender(replyMessage),
                replyMessage.getContent()
        );
    }

    private String resolveSender(ChatMessage message) {
        User user = message.getUser();

        if (message.getMessageType() == MessageType.SYSTEM || user == null) {
            return SYSTEM;
        }

        return user.getNickname();
    }

    private UUID extractUserId(ChatMessage message) {
        User user = message.getUser();

        if (message.getMessageType() == MessageType.SYSTEM || user == null) {
            return null;
        }

        return user.getUserId();
    }

    private Long extractGroupId(ChatMessage message) {
        Group group = message.getGroup();
        return group != null ? group.getId() : null;
    }
}
