package com.solv.wefin.domain.chat.globalChat.repository;

import com.solv.wefin.domain.chat.globalChat.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {
}
