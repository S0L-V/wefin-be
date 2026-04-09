package com.solv.wefin.domain.trading.snapshot.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;

@Repository
public interface DailySnapshotRepository extends JpaRepository<DailySnapshot, Long> {

	List<DailySnapshot> findByVirtualAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
		Long virtualAccountId, LocalDate from, LocalDate to);

	List<DailySnapshot> findBySnapshotDate(LocalDate snapshotDate);

	boolean existsByVirtualAccountIdAndSnapshotDate(Long virtualAccountId, LocalDate snapshotDate);

	Optional<DailySnapshot> findTopByVirtualAccountIdOrderBySnapshotDateDesc(Long virtualAccountId);
}
