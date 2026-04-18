package com.solv.wefin.domain.quest.repository;

import com.solv.wefin.domain.quest.entity.QuestTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestTemplateRepository extends JpaRepository<QuestTemplate, Long> {

    List<QuestTemplate> findByActiveTrue();
}
