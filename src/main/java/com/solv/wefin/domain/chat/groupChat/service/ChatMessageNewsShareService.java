package com.solv.wefin.domain.chat.groupChat.service;

import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import com.solv.wefin.domain.chat.groupChat.entity.ChatMessageNewsShare;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageNewsShareRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageNewsShareService {

    private final ChatMessageNewsShareRepository chatMessageNewsShareRepository;

    public ChatMessageNewsShare save(ChatMessage chatMessage, NewsCluster newsCluster) {
        ChatMessageNewsShare newsShare = ChatMessageNewsShare.create(chatMessage, newsCluster);
        chatMessage.attachNewsShare(newsShare);
        return chatMessageNewsShareRepository.save(newsShare);
    }
}
