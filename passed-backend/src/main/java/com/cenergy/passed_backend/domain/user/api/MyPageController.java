package com.cenergy.passed_backend.domain.user.api;

import com.cenergy.passed_backend.domain.user.application.MyPageQueryService;
import com.cenergy.passed_backend.domain.user.dto.MyPageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class MyPageController {
    private final MyPageQueryService service;

    public MyPageController(MyPageQueryService service) {
        this.service = service;
    }

    @GetMapping("/mypage")
    public ResponseEntity<MyPageResponse> findMine() {
        return ResponseEntity.ok(service.findMine());
    }
}
