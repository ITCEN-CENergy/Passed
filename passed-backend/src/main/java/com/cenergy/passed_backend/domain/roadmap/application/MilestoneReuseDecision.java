package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import com.cenergy.passed_backend.domain.roadmap.entity.ReuseType;

public record MilestoneReuseDecision(ReuseType reuseType, Milestone milestone, String reason) {
    public static MilestoneReuseDecision reused(Milestone milestone) {
        return new MilestoneReuseDecision(
                ReuseType.REUSED,
                milestone,
                "동일 역량과 학습 유형의 기존 마일스톤이 필요한 레벨 범위를 포함합니다."
        );
    }

    public static MilestoneReuseDecision newMilestone() {
        return new MilestoneReuseDecision(ReuseType.NEW, null, null);
    }
}
