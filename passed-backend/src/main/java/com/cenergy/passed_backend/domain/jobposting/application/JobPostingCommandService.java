package com.cenergy.passed_backend.domain.jobposting.application;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingSkillCreateRequest;
import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.CompanyRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingSkillRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JobPostingCommandService {
    private final JobPostingRepository jobPostingRepository;
    private final JobPostingSkillRepository jobPostingSkillRepository;
    private final CompanyRepository companyRepository;
    private final JobRoleRepository jobRoleRepository;
    private final SkillRepository skillRepository;

    public JobPostingCommandService(
            JobPostingRepository jobPostingRepository,
            JobPostingSkillRepository jobPostingSkillRepository,
            CompanyRepository companyRepository,
            JobRoleRepository jobRoleRepository,
            SkillRepository skillRepository
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobPostingSkillRepository = jobPostingSkillRepository;
        this.companyRepository = companyRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public JobPostingCreateResponse create(JobPostingCreateRequest request) {
        validateRequest(request);
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> notFound(
                        ErrorCode.JOB_POSTING_COMPANY_NOT_FOUND,
                        "Company not found"
                ));
        JobRole jobRole = jobRoleRepository.findById(request.jobRoleId())
                .orElseThrow(() -> notFound(
                        ErrorCode.JOB_POSTING_JOB_ROLE_NOT_FOUND,
                        "Job role not found"
                ));
        Map<Long, Skill> skillsById = loadSkills(request);

        JobPosting posting = jobPostingRepository.save(JobPosting.create(
                request.title(),
                company,
                jobRole,
                request.startYmd(),
                request.endYmd(),
                request.headcount(),
                request.careerType(),
                request.hireType(),
                request.region(),
                request.educationLevel(),
                request.positionDetail(),
                request.mainDuty(),
                request.qualification(),
                request.preference(),
                request.disqualification(),
                request.process()
        ));

        List<JobPostingSkill> postingSkills = new ArrayList<>();
        postingSkills.addAll(toEntities(
                posting,
                request.requiredSkills(),
                JobPostingSkillType.REQUIRED,
                skillsById
        ));
        postingSkills.addAll(toEntities(
                posting,
                request.preferredSkills(),
                JobPostingSkillType.PREFERRED,
                skillsById
        ));
        jobPostingSkillRepository.saveAll(postingSkills);

        return new JobPostingCreateResponse(
                posting.getId(),
                request.requiredSkills().size(),
                request.preferredSkills().size()
        );
    }

    private void validateRequest(JobPostingCreateRequest request) {
        if (request == null) {
            throw invalid("request must not be null");
        }
        if (request.title() == null || request.title().isBlank()
                || request.companyId() == null || request.companyId() <= 0
                || request.jobRoleId() == null || request.jobRoleId() <= 0
                || request.headcount() != null && request.headcount() <= 0) {
            throw invalid("title, companyId, jobRoleId and headcount are invalid");
        }
        if (request.requiredSkills() == null || request.requiredSkills().isEmpty()
                || request.preferredSkills() == null) {
            throw invalid("requiredSkills must not be empty and preferredSkills must not be null");
        }
        LocalDate startDate = parseDate(request.startYmd(), "startYmd");
        LocalDate endDate = parseDate(request.endYmd(), "endYmd");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw invalid("startYmd must not be after endYmd");
        }
        Set<Long> skillIds = new LinkedHashSet<>();
        for (JobPostingSkillCreateRequest skill : allSkills(request)) {
            if (skill == null || skill.skillId() == null || skill.skillId() <= 0
                    || skill.skillLevel() < 1 || skill.skillLevel() > 3) {
                throw invalid("Every skill requires a positive skillId and skillLevel between 1 and 3");
            }
            if (!skillIds.add(skill.skillId())) {
                throw invalid("A skill must not be duplicated in one job posting");
            }
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw invalid(fieldName + " must be a valid yyyyMMdd date");
        }
    }

    private Map<Long, Skill> loadSkills(JobPostingCreateRequest request) {
        Set<Long> requestedIds = new LinkedHashSet<>();
        allSkills(request).forEach(value -> requestedIds.add(value.skillId()));
        Map<Long, Skill> skillsById = new LinkedHashMap<>();
        skillRepository.findAllByIdIn(requestedIds)
                .forEach(skill -> skillsById.put(skill.getId(), skill));
        if (skillsById.size() != requestedIds.size()) {
            throw notFound(
                    ErrorCode.JOB_POSTING_SKILL_NOT_FOUND,
                    "One or more skills were not found"
            );
        }
        return skillsById;
    }

    private List<JobPostingSkillCreateRequest> allSkills(JobPostingCreateRequest request) {
        List<JobPostingSkillCreateRequest> result = new ArrayList<>(request.requiredSkills());
        result.addAll(request.preferredSkills());
        return result;
    }

    private List<JobPostingSkill> toEntities(
            JobPosting posting,
            List<JobPostingSkillCreateRequest> requests,
            JobPostingSkillType type,
            Map<Long, Skill> skillsById
    ) {
        return requests.stream()
                .map(request -> JobPostingSkill.create(
                        posting,
                        skillsById.get(request.skillId()),
                        type,
                        request.skillLevel()
                ))
                .toList();
    }

    private JobPostingException invalid(String message) {
        return new JobPostingException(ErrorCode.JOB_POSTING_INVALID_REQUEST, message);
    }

    private JobPostingException notFound(ErrorCode code, String message) {
        return new JobPostingException(code, message);
    }
}
