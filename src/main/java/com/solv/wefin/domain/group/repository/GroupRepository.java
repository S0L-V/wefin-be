package com.solv.wefin.domain.group.repository;


import com.solv.wefin.domain.group.entity.Group;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Group> findById(Long groupId);
}