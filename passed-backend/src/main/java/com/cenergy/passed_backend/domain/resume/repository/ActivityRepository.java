package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findAllByResumeIdOrderById(Long resumeId);
}
