package com.gooroomees.neulbomgil_backend.domain.auth.controller;

import com.gooroomees.neulbomgil_backend.domain.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;

    @GetMapping("/test")
    public void sendTestMail() {
        emailService.sendEmail("minho2618@naver.com", "Test Mail", "SMTP 테스트용 메일입니다.");
    }


}