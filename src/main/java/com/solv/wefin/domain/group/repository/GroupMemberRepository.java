package com.solv.wefin.domain.group.repository;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByUserAndGroup(User user, Group group);
}