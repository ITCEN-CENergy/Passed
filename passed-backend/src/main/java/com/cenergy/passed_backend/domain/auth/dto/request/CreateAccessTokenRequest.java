package com.gooroomees.neulbomgil_backend.domain.auth.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccessTokenRequest {
    private String refreshToken;
}
