package com.solv.wefin.domain.quest.repository;

import com.solv.wefin.domain.quest.entity.UserQuest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserQuestRepository extends JpaRepository<UserQuest, Long> {

    @Query("""
    select uq
    from UserQuest uq
    join fetch uq.dailyQuest dq
    join fetch dq.questTemplate qt
    where uq.user.userId = :userId
      and dq.questDate = :questDate
    order by uq.id asc
    """)
    List<UserQuest> findTodayUserQuests(UUID userId, LocalDate questDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select uq
    from UserQuest uq
    join fetch uq.dailyQuest dq
    join fetch dq.questTemplate qt
    where uq.id = :questId
    and uq.user.userId = :userId
    """)
    Optional<UserQuest> findByIdAndUserIdForUpdate(Long questId, UUID userId);
}
