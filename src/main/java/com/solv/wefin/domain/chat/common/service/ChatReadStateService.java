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

import java.util.Optional;
import java.util.UUID;

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

        return new ChatUnreadInfo(
                globalUnreadCount,
                groupUnreadCount,
                user.getLastReadGlobalMessageId(),
                groupMember.getLastReadChatMessageId()
        );
    }

    @Transactional(readOnly = true)
    public ChatUnreadInfo getUnreadInfoSnapshot(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<GroupMember> groupMemberOpt = groupMemberRepository
                .findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE);
        long globalUnreadCount = countGlobalUnread(user.getLastReadGlobalMessageId(), userId);
        long groupUnreadCount = groupMemberOpt
                .map(gm -> countGroupUnread(gm.getGroup().getId(), gm.getLastReadChatMessageId(), userId))
                .orElse(0L);

        return new ChatUnreadInfo(
                globalUnreadCount,
                groupUnreadCount,
                user.getLastReadGlobalMessageId(),
                groupMemberOpt.map(GroupMember::getLastReadChatMessageId).orElse(null)
        );
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

        return countGlobalUnread(lastReadMessageId, user.getUserId());
    }

    private long initializeAndCountGroupUnread(GroupMember groupMember) {
        Long lastReadMessageId = groupMember.getLastReadChatMessageId();
        if (lastReadMessageId == null) {
            groupMember.markGroupChatRead(
                    chatMessageRepository.findLatestMessageIdByGroupId(groupMember.getGroup().getId())
            );
            return 0L;
        }

        return countGroupUnread(groupMember.getGroup().getId(), lastReadMessageId, groupMember.getUser().getUserId());
    }

    private long countGlobalUnread(Long lastReadMessageId, UUID userId) {
        if (lastReadMessageId == null) {
            return 0L;
        }

        return globalChatMessageRepository.countUnreadAfterMessageId(lastReadMessageId, userId);
    }

    private long countGroupUnread(Long groupId, Long lastReadMessageId, UUID userId) {
        if (lastReadMessageId == null) {
            return 0L;
        }

        return chatMessageRepository.countUnreadAfterMessageId(groupId, lastReadMessageId, userId);
    }
}
