package com.solv.wefin.domain.game.news.repository;

import com.solv.wefin.domain.game.news.entity.GameNewsArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface GameNewsArchiveRepository extends JpaRepository<GameNewsArchive, UUID> {


    List<GameNewsArchive> findByPublishedAtBetweenOrderByPublishedAtDesc(
            OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT gna.originalUrl FROM GameNewsArchive gna WHERE gna.originalUrl IN :urls")
    Set<String> findExistingUrls(@Param("urls") List<String> urls);
}


