package com.groupcc2.recruitment.users.repository;

import java.util.Optional;
import java.util.UUID;

import com.groupcc2.recruitment.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
