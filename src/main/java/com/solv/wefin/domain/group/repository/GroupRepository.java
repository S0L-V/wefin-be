package com.solv.wefin.domain.group.repository;


import com.solv.wefin.domain.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {
}