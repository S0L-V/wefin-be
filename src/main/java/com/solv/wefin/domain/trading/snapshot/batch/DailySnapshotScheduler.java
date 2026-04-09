package com.solv.wefin.domain.trading.snapshot.batch;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.solv.wefin.domain.trading.snapshot.service.SnapshotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySnapshotScheduler {

	private final SnapshotService snapshotService;

	private final AtomicBoolean running = new AtomicBoolean(false);

	@Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
	public void createDailySnapshots() {
		if (!running.compareAndSet(false, true)) {
			log.info("스냅샷 배치 이미 실행 중, 스킵");
			return;
		}

		try {
			LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
			snapshotService.createAllSnapshots(today);
		} catch (Exception e) {
			log.error("스냅샷 배치 실패", e);
		} finally {
			running.set(false);
		}
	}
}
