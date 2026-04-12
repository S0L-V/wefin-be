package com.solv.wefin.domain.quest.repository;

import com.solv.wefin.domain.quest.entity.UserQuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UserQuestRepository extends JpaRepository<UserQuest, Long> {

    boolean existsByUser_UserIdAndDailyQuest_QuestDate(UUID userId, LocalDate questDate);

    @Query("""
    select uq
    from UserQuest uq
    join fetch uq.dailyQuest dq
    join fetch dq.questTemplate qt
    join fetch uq.user u
    where u.userId = :userId
      and dq.questDate = :questDate
    order by uq.id asc
    """)
    List<UserQuest> findAllByUser_UserIdAndDailyQuest_QuestDateOrderByIdAsc(UUID userId, LocalDate questDate);

    List<UserQuest> findAllByUser_UserIdOrderByIdDesc(UUID userId);

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

}
