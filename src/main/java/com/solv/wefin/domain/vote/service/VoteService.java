package com.solv.wefin.domain.vote.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.groupChat.service.ChatMessageService;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.repository.GroupRepository;
import com.solv.wefin.domain.vote.dto.command.CreateVoteCommand;
import com.solv.wefin.domain.vote.dto.command.SubmitVoteCommand;
import com.solv.wefin.domain.vote.dto.info.*;
import com.solv.wefin.domain.vote.entity.Vote;
import com.solv.wefin.domain.vote.entity.VoteAnswer;
import com.solv.wefin.domain.vote.entity.VoteOption;
import com.solv.wefin.domain.vote.entity.VoteStatus;
import com.solv.wefin.domain.vote.repository.VoteAnswerRepository;
import com.solv.wefin.domain.vote.repository.VoteOptionRepository;
import com.solv.wefin.domain.vote.repository.VoteRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {

    private static final Set<Long> ALLOWED_DURATION_HOURS = Set.of(1L, 4L, 8L, 24L, 72L);

    private final VoteRepository voteRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final VoteAnswerRepository voteAnswerRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ChatMessageService chatMessageService;

    @Transactional
    public VoteInfo createVote(UUID userId, CreateVoteCommand command) {
        validateCreateCommand(command);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Group group = groupRepository.findById(command.groupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        OffsetDateTime endsAt = OffsetDateTime.now().plusHours(command.durationHours());

        Vote vote = Vote.create(
                group,
                user,
                command.title(),
                endsAt,
                command.maxSelectCount()
        );

        voteRepository.save(vote);

        List<VoteOption> options = command.options().stream()
                .map(optionText -> VoteOption.create(vote, optionText))
                .toList();

        voteOptionRepository.saveAll(options);

        chatMessageService.shareVote(userId, vote);

        return new VoteInfo(
                vote.getVoteId(),
                vote.getTitle(),
                vote.getStatus(),
                vote.getMaxSelectCount(),
                vote.getEndsAt()
        );
    }

    public VoteDetailInfo getVoteDetail(UUID userId, Long voteId) {
        Vote vote = getVote(voteId);

        List<VoteOption> options = voteOptionRepository.findAllByVote_VoteIdOrderByIdAsc(voteId);
        List<VoteAnswer> myAnswers = voteAnswerRepository.findAllByVote_VoteIdAndUser_UserId(voteId, userId);

        List<VoteOptionInfo> optionInfos = options.stream()
                .map(option -> new VoteOptionInfo(option.getId(), option.getOptionText()))
                .toList();

        List<Long> myOptionIds = myAnswers.stream()
                .map(answer -> answer.getVoteOption().getId())
                .toList();

        return new VoteDetailInfo(
                vote.getVoteId(),
                vote.getTitle(),
                vote.getStatus(),
                vote.getMaxSelectCount(),
                vote.getEndsAt(),
                isClosed(vote),
                optionInfos,
                myOptionIds
        );
    }

    @Transactional
    public VoteResultInfo submitVote(UUID userId, Long voteId, SubmitVoteCommand command) {
        Vote vote = getVote(voteId);

        if (isClosed(vote)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        validateSubmitCommand(command, vote.getMaxSelectCount());

        List<VoteOption> options = voteOptionRepository.findAllByIdIn(command.optionIds());

        if (options.size() != command.optionIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        boolean allBelongToVote = options.stream()
                .allMatch(option -> option.getVote().getVoteId().equals(voteId));

        if (!allBelongToVote) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        voteAnswerRepository.deleteAllByVote_VoteIdAndUser_UserId(voteId, userId);
        voteAnswerRepository.flush();

        List<VoteAnswer> answers = options.stream()
                .map(option -> VoteAnswer.voted(option, vote, user))
                .toList();

        voteAnswerRepository.saveAll(answers);

        return getVoteResult(userId, voteId);
    }

    public VoteResultInfo getVoteResult(UUID userId, Long voteId) {
        Vote vote = getVote(voteId);

        List<VoteOption> options = voteOptionRepository.findAllByVote_VoteIdOrderByIdAsc(voteId);
        List<VoteAnswer> myAnswers = voteAnswerRepository.findAllByVote_VoteIdAndUser_UserId(voteId, userId);
        long participantCount = voteAnswerRepository.countDistinctUsersByVoteId(voteId);

        Set<Long> myOptionIds = myAnswers.stream()
                .map(answer -> answer.getVoteOption().getId())
                .collect(Collectors.toSet());

        Map<Long, Long> countMap = voteAnswerRepository.countByVoteIdGroupByOptionId(voteId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        List<VoteOptionResultInfo> optionResults = options.stream()
                .map(option -> {
                    long voteCount = countMap.getOrDefault(option.getId(), 0L);
                    double rate = participantCount == 0 ? 0.0 : (double) voteCount / participantCount * 100;

                    return new VoteOptionResultInfo(
                            option.getId(),
                            option.getOptionText(),
                            voteCount,
                            rate,
                            myOptionIds.contains(option.getId())
                    );
                })
                .toList();

        return new VoteResultInfo(
                vote.getVoteId(),
                vote.getTitle(),
                vote.getStatus(),
                vote.getMaxSelectCount(),
                vote.getEndsAt(),
                isClosed(vote),
                participantCount,
                optionResults
        );
    }

    private Vote getVote(Long voteId) {
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        closeIfExpired(vote);
        return vote;
    }

    private void validateCreateCommand(CreateVoteCommand command) {
        if (command.title() == null || command.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (command.options() == null || command.options().size() < 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (command.maxSelectCount() <= 0 || command.maxSelectCount() > command.options().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!ALLOWED_DURATION_HOURS.contains(command.durationHours())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateSubmitCommand(SubmitVoteCommand command, int maxSelectCount) {
        if (command.optionIds() == null || command.optionIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (command.optionIds().size() > maxSelectCount) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        long distinctCount = command.optionIds().stream().distinct().count();
        if (distinctCount != command.optionIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isClosed(Vote vote) {
        return vote.getStatus() == VoteStatus.CLOSED;
    }

    private void closeIfExpired(Vote vote) {
        if (vote.getStatus() == VoteStatus.OPEN
                && vote.getEndsAt() != null
                && OffsetDateTime.now().isAfter(vote.getEndsAt())) {
            vote.close();
        }
    }
}
