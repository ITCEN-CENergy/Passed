package com.gooroomees.neulbomgil_backend.domain.auth.repository;

import com.gooroomees.neulbomgil_backend.domain.auth.entity.Role;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.Status;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAuthRepository extends JpaRepository<UserAuth, Long> {
    Optional<UserAuth> findByEmail(String email);

    boolean existsByEmail(String email);


    List<UserAuth> findByStatus(Status status);

    List<UserAuth> findByRole(Role role);
}