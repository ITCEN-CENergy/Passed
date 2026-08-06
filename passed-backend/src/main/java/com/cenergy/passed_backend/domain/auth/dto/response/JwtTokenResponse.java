package com.gooroomees.neulbomgil_backend.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtTokenResponse {
    private String accessToken;
    private String refreshToken;
}
