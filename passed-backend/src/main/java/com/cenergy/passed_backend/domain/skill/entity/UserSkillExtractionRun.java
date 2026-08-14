package com.cenergy.passed_backend.domain.skill.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "user_skill_extraction_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkillExtractionRun extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserSkillExtractionRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 40)
    private UserSkillExtractionStage stage;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public static UserSkillExtractionRun start(User user) {
        UserSkillExtractionRun run = new UserSkillExtractionRun();
        run.user = user;
        run.status = UserSkillExtractionRunStatus.PROCESSING;
        run.stage = UserSkillExtractionStage.DOCUMENT_ANALYSIS;
        return run;
    }

    public void moveTo(UserSkillExtractionStage stage) {
        if (status != UserSkillExtractionRunStatus.PROCESSING) return;
        this.stage = stage;
    }

    public void complete() {
        status = UserSkillExtractionRunStatus.COMPLETED;
        stage = UserSkillExtractionStage.COMPLETED;
        completedAt = OffsetDateTime.now();
        failureMessage = null;
    }

    public void fail(String message) {
        status = UserSkillExtractionRunStatus.FAILED;
        stage = UserSkillExtractionStage.FAILED;
        completedAt = OffsetDateTime.now();
        failureMessage = message == null || message.isBlank()
                ? "스킬 분석을 완료하지 못했습니다."
                : message;
    }
}
