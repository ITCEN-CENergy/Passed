package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.application.MockSkillGapService;
import com.cenergy.passed_backend.domain.skillgap.application.SkillGapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SkillGapServiceBeanTest {
    @Autowired
    private SkillGapService skillGapService;

    @Test
    void usesMockUntilRealServiceIsProvided() {
        assertThat(skillGapService).isInstanceOf(MockSkillGapService.class);
    }
}
