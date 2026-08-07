package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public boolean isEmailExisting(String email) {
        return userRepository.existsByEmail(email);
    }
}