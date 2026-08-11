package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.Training;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrainingRepository extends JpaRepository<Training, Long> {
    List<Training> findAllByResumeIdOrderById(Long resumeId);
}
