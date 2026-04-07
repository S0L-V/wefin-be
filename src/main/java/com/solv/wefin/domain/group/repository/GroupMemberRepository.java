package com.solv.wefin.domain.group.repository;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByUserAndGroup(User user, Group group);

    Optional<GroupMember> findByUser_UserIdAndStatus(
            UUID userId,
            GroupMember.GroupMemberStatus status
    );

    boolean existsByUser_UserIdAndGroupAndStatus(
            UUID userId,
            Group group,
            GroupMember.GroupMemberStatus status
    );

    @Query("""
            select gm
            from GroupMember gm
            join fetch gm.user
            where gm.group = :group
              and gm.status = :status
            """)
    List<GroupMember> findByGroupAndStatusWithUser(@Param("group") Group group,
                                                   @Param("status") GroupMember.GroupMemberStatus status);

    long countByGroupAndStatus(Group group, GroupMember.GroupMemberStatus status);
}