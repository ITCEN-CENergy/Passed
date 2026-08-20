package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRun;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionStage;
import com.cenergy.passed_backend.domain.skill.repository.UserSkillExtractionRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSkillExtractionRunStateService {
    private final UserSkillExtractionRunRepository repository;

    public UserSkillExtractionRunStateService(UserSkillExtractionRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void moveTo(Long runId, UserSkillExtractionStage stage) {
        find(runId).moveTo(stage);
    }

    @Transactional
    public void complete(Long runId) {
        find(runId).complete();
    }

    @Transactional
    public void fail(Long runId, Throwable failure) {
        find(runId).fail(failure == null ? null : failure.getMessage());
    }

    private UserSkillExtractionRun find(Long runId) {
        return repository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Skill extraction run not found: " + runId));
    }
}
