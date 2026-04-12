package com.solv.wefin.domain.quest.repository;

import com.solv.wefin.domain.quest.entity.DailyQuest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyQuestRepository extends JpaRepository<DailyQuest, Long> {

    List<DailyQuest> findAllByQuestDate(LocalDate questDate);
}
