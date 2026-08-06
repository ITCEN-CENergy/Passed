package com.gooroomees.neulbomgil_backend.domain.auth.repository;

import com.gooroomees.neulbomgil_backend.domain.auth.entity.Role;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.UserAuth;
import com.gooroomees.neulbomgil_backend.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface UserChatRepository  extends JpaRepository<UserAuth, Long> {

   // Optional<UserAuth> findFirstByRole(Role role);

    @Query("""
    select cr.roomId
    from ChatRoom cr
    where cr.user.userId = :userId
""")
    Integer findRoomIdByUserId(@Param("userId") Long userId);
}
