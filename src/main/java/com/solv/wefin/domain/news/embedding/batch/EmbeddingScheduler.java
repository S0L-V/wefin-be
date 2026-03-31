package com.solv.wefin.domain.news.embedding.batch;

import com.solv.wefin.domain.news.embedding.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 임베딩 생성 스케줄러
 *
 * 30분 간격으로 크롤링 완료 기사의 임베딩을 생성한다.
 * AtomicBoolean으로 수동 트리거와의 동시 실행을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingScheduler {

    private final EmbeddingService embeddingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${embedding.collect.cron:0 */30 * * * *}")
    public void generateEmbeddings() {
        execute();
    }

    /**
     * 임베딩 생성을 실행한다.
     *
     * @return 실행 여부 (이미 실행 중이면 false)
     */
    public boolean execute() {
        if (!running.compareAndSet(false, true)) {
            log.info("임베딩 생성이 이미 실행 중입니다. 스킵합니다.");
            return false;
        }

        log.info("=== 임베딩 생성 배치 시작 ===");
        long start = System.currentTimeMillis();

        try {
            embeddingService.generatePendingEmbeddings();
            return true;
        } catch (Exception e) {
            log.error("임베딩 생성 실패: {}", e.getMessage(), e);
            return false;
        } finally {
            running.set(false);
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 임베딩 생성 배치 종료 ({}ms) ===", elapsed);
        }
    }
}
