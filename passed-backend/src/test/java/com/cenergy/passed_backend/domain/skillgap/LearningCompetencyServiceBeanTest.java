package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.application.MockLearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.application.LearningCompetencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LearningCompetencyServiceBeanTest {
    @Autowired
    private LearningCompetencyService learningCompetencyService;

    @Test
    void usesMockUntilRealServiceIsProvided() {
        assertThat(learningCompetencyService).isInstanceOf(MockLearningCompetencyService.class);
    }
}
