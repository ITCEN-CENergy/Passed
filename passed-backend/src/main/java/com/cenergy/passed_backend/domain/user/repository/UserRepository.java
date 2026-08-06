package com.cenergy.passed_backend.domain.user.repository;

import com.cenergy.passed_backend.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
}