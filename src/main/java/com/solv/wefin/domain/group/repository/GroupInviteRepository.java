package com.solv.wefin.domain.group.repository;

import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    Optional<GroupInvite> findByInviteCode(UUID inviteCode);

    Optional<GroupInvite> findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
            Group group,
            GroupInvite.InviteStatus status,
            OffsetDateTime now
    );
}