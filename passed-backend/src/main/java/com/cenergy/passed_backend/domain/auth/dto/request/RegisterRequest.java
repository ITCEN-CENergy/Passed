package com.gooroomees.neulbomgil_backend.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "회원 가입 요청 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @Schema(description = "사용자 아이디", example = "user123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "비밀번호", example = "pass1234!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "이메일 주소", example = "minho@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}