package com.solv.wefin.domain.group.repository;

import com.solv.wefin.domain.group.entity.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    Optional<GroupInvite> findByInviteCode(UUID inviteCode);
}