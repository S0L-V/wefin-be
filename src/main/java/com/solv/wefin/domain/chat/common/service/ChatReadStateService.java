package com.solv.wefin.domain.chat.common.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.dto.info.ChatUnreadInfo;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.domain.chat.groupChat.repository.ChatMessageRepository;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatReadStateService {

    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GlobalChatMessageRepository globalChatMessageRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatUnreadInfo getUnreadInfo(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GroupMember groupMember = groupMemberRepository.findByUser_UserIdAndStatus(
                        userId,
                        GroupMember.GroupMemberStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_FORBIDDEN));

        long globalUnreadCount = initializeAndCountGlobalUnread(user);
        long groupUnreadCount = initializeAndCountGroupUnread(groupMember);

        return new ChatUnreadInfo(globalUnreadCount, groupUnreadCount);
    }

    @Transactional(readOnly = true)
    public ChatUnreadInfo getUnreadInfoSnapshot(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GroupMember groupMember = groupMemberRepository.findByUser_UserIdAndStatus(
                        userId,
                        GroupMember.GroupMemberStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_FORBIDDEN));

        long globalUnreadCount = countGlobalUnread(user.getLastReadGlobalMessageId());
        long groupUnreadCount = countGroupUnread(groupMember.getGroup().getId(), groupMember.getLastReadChatMessageId());

        return new ChatUnreadInfo(globalUnreadCount, groupUnreadCount);
    }

    @Transactional
    public void markGlobalChatRead(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long latestMessageId = globalChatMessageRepository.findLatestMessageId();
        user.markGlobalChatRead(latestMessageId);
    }

    @Transactional
    public void markGroupChatRead(UUID userId) {
        GroupMember groupMember = groupMemberRepository.findByUser_UserIdAndStatus(
                        userId,
                        GroupMember.GroupMemberStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_FORBIDDEN));

        Long latestMessageId = chatMessageRepository.findLatestMessageIdByGroupId(groupMember.getGroup().getId());
        groupMember.markGroupChatRead(latestMessageId);
    }

    private long initializeAndCountGlobalUnread(User user) {
        Long lastReadMessageId = user.getLastReadGlobalMessageId();
        if (lastReadMessageId == null) {
            user.markGlobalChatRead(globalChatMessageRepository.findLatestMessageId());
            return 0L;
        }

        return countGlobalUnread(lastReadMessageId);
    }

    private long initializeAndCountGroupUnread(GroupMember groupMember) {
        Long lastReadMessageId = groupMember.getLastReadChatMessageId();
        if (lastReadMessageId == null) {
            groupMember.markGroupChatRead(
                    chatMessageRepository.findLatestMessageIdByGroupId(groupMember.getGroup().getId())
            );
            return 0L;
        }

        return countGroupUnread(groupMember.getGroup().getId(), lastReadMessageId);
    }

    private long countGlobalUnread(Long lastReadMessageId) {
        if (lastReadMessageId == null) {
            return 0L;
        }

        return globalChatMessageRepository.countByIdGreaterThan(lastReadMessageId);
    }

    private long countGroupUnread(Long groupId, Long lastReadMessageId) {
        if (lastReadMessageId == null) {
            return 0L;
        }

        return chatMessageRepository.countByGroup_IdAndIdGreaterThan(groupId, lastReadMessageId);
    }
}
