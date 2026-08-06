package com.gooroomees.neulbomgil_backend.domain.auth.dto.response;

import com.gooroomees.neulbomgil_backend.domain.auth.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
     private Long userId;
     private String email;
     private String name;
     private String role;
}
