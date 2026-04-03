package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.globalChat.dto.command.GlobalProfitShareCommand;
import com.solv.wefin.domain.chat.globalChat.dto.info.GlobalChatMessageInfo;
import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalChatService {

    private final ApplicationEventPublisher eventPublisher;
    private final GlobalChatMessageRepository globalChatMessageRepository;
    private final UserRepository userRepository;
    private final ChatSpamGuard chatSpamGuard;

    private static final long SPAM_WINDOW_SECONDS = 3L;
    private static final String SYSTEM = "시스템";
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final Map<String, Object> chatLocks = new ConcurrentHashMap<>();
    @Transactional
    public void sendMessage(String content, UUID userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        validateMessage(content);

        String blockKey = ChatScope.globalKey(userId);
        Object lock = chatLocks.computeIfAbsent(blockKey, key -> new Object());

        /*
          같은 유저가 동시에 두 요청을 보내면 둘 다 같은 count를 보고 통과할 수 있음
           -> synchronized를 통해 blockKey 단위로 직렬화
           단일 서버 기준 최소 방어
          */

        synchronized (lock) {
            OffsetDateTime now = OffsetDateTime.now();

            long recentCount = globalChatMessageRepository.countByUser_UserIdAndCreatedAtAfter(
                    userId,
                    now.minusSeconds(SPAM_WINDOW_SECONDS)
            );

            // 도배 체크
            chatSpamGuard.validate(blockKey, recentCount, now);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                    GlobalChatMessage.createUserMessage(user, content)
            );

            eventPublisher.publishEvent(toEvent(savedMessage));
        }
    }

    @Transactional
    public void sendSystemMessage(String content) {

        validateMessage(content);

        GlobalChatMessage savedMessage = globalChatMessageRepository.save(
                GlobalChatMessage.createSystemMessage(content)
        );

        eventPublisher.publishEvent(toEvent(savedMessage));
    }

    @Transactional
    public void sendProfitShareMessage(GlobalProfitShareCommand command) {
        String content = createProfitShareMessage(command);
        sendSystemMessage(content);
    }


    private GlobalChatMessageInfo toInfo(GlobalChatMessage message) {
        return new GlobalChatMessageInfo(
                message.getId(),
                extractUserId(message),
                message.getRole().name(),
                resolveSender(message),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private GlobalChatMessageCreatedEvent toEvent(GlobalChatMessage message) {
        return new GlobalChatMessageCreatedEvent(
                message.getId(),
                extractUserId(message),
                message.getRole().name(),
                resolveSender(message),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private void validateMessage(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }

    public List<GlobalChatMessageInfo> getRecentMessages(int limit) {

        int size = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, size);

        List<GlobalChatMessage> messages = globalChatMessageRepository.findRecentMessages(pageable);

        return messages.stream()
                .sorted(Comparator.comparing(GlobalChatMessage::getId))
                .map(this::toInfo)
                .toList();
    }

    // 수익 메시지 변환
    private String createProfitShareMessage(GlobalProfitShareCommand command) {
        String nickname = command.userNickname();
        String stockName = command.stockName();
        long profitAmount = command.profitAmount();
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);

        if (profitAmount > 0) {
            String amount = numberFormat.format(profitAmount);
            return "축하합니다! " + nickname + "님이 " + stockName + "에서 " + amount + "원의 수익을 달성하셨습니다!";
        } else if (profitAmount < 0) {
            String amount = numberFormat.format(Math.abs(profitAmount));
            return "안타깝네요. " + nickname + "님이 " + stockName + "에서 " + amount + "원을 잃었습니다.";
        }

        throw new BusinessException(ErrorCode.INVALID_PROFIT_AMOUNT);
    }

    private String resolveSender(GlobalChatMessage message) {
        User user = message.getUser();

        if (message.getRole() == ChatRole.SYSTEM || user == null) {
            return SYSTEM;
        }

        return user.getNickname();
    }

    private UUID extractUserId(GlobalChatMessage message) {
        User user = message.getUser();

        if (message.getRole() == ChatRole.SYSTEM || user == null) {
            return null;
        }

        return user.getUserId();
    }
}
