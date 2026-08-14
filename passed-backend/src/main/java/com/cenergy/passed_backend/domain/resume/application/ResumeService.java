package com.cenergy.passed_backend.domain.resume.application;

import com.cenergy.passed_backend.domain.resume.dto.ResumeResponse;
import com.cenergy.passed_backend.domain.resume.dto.ResumeUpsertRequest;
import com.cenergy.passed_backend.domain.resume.entity.*;
import com.cenergy.passed_backend.domain.resume.repository.*;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 사용자당 하나인 이력서와 모든 하위 항목을 한 트랜잭션으로 관리한다.
 *
 * <p>Q. 왜 학력·경력마다 별도 등록 API를 만들지 않나요?</p>
 * <p>A. 화면의 최종 이력서 한 장이 하나의 스냅샷이므로, 부모와 하위 항목을 함께
 * 성공시키거나 함께 롤백해야 중간 저장 상태가 생기지 않기 때문이다.</p>
 *
 * <p>Q. 수정 때 하위 행을 전부 삭제하고 다시 만들면 안 되나요?</p>
 * <p>A. 청킹 파이프라인이 하위 행 ID를 source_id로 사용한다. 변경 없는 행의 ID를
 * 유지해야 불필요한 청크 재생성과 재임베딩을 줄일 수 있다.</p>
 */
@Service
public class ResumeService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PersonalInfoRepository personalInfoRepository;
    private final EducationRepository educationRepository;
    private final ExperienceRepository experienceRepository;
    private final ActivityRepository activityRepository;
    private final TrainingRepository trainingRepository;
    private final CertificationRepository certificationRepository;
    private final AwardRepository awardRepository;
    private final OverseasExperienceRepository overseasExperienceRepository;
    private final LanguageProficiencyRepository languageProficiencyRepository;

    public ResumeService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            ResumeRepository resumeRepository,
            PersonalInfoRepository personalInfoRepository,
            EducationRepository educationRepository,
            ExperienceRepository experienceRepository,
            ActivityRepository activityRepository,
            TrainingRepository trainingRepository,
            CertificationRepository certificationRepository,
            AwardRepository awardRepository,
            OverseasExperienceRepository overseasExperienceRepository,
            LanguageProficiencyRepository languageProficiencyRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.personalInfoRepository = personalInfoRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
        this.activityRepository = activityRepository;
        this.trainingRepository = trainingRepository;
        this.certificationRepository = certificationRepository;
        this.awardRepository = awardRepository;
        this.overseasExperienceRepository = overseasExperienceRepository;
        this.languageProficiencyRepository = languageProficiencyRepository;
    }

    @Transactional
    public ResumeResponse create(ResumeUpsertRequest request) {
        validateBusinessRules(request);
        Long userId = currentUserId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> error(ErrorCode.RESUME_USER_NOT_FOUND, "Current user not found"));
        if (resumeRepository.existsByUserId(userId)) {
            throw error(ErrorCode.RESUME_ALREADY_EXISTS, "Resume already exists");
        }

        Resume resume = resumeRepository.save(Resume.create(user));
        PersonalInfo personalInfo = PersonalInfo.create(resume);
        apply(personalInfo, request.personalInfo());
        personalInfoRepository.save(personalInfo);

        saveNewChildren(resume, request);
        return loadResponse(resume);
    }

    @Transactional(readOnly = true)
    public ResumeResponse findMine() {
        Resume resume = resumeRepository.findByUserId(currentUserId())
                .orElseThrow(() -> error(ErrorCode.RESUME_NOT_FOUND, "Resume not found"));
        return loadResponse(resume);
    }

    @Transactional
    public ResumeResponse update(ResumeUpsertRequest request) {
        validateBusinessRules(request);
        Resume resume = resumeRepository.findByUserIdForUpdate(currentUserId())
                .orElseThrow(() -> error(ErrorCode.RESUME_NOT_FOUND, "Resume not found"));
        ResumeResponse beforeUpdate = loadResponse(resume);

        PersonalInfo personalInfo = personalInfoRepository.findByResumeId(resume.getId())
                .orElseGet(() -> PersonalInfo.create(resume));
        apply(personalInfo, request.personalInfo());
        personalInfoRepository.save(personalInfo);

        syncEducations(resume, request.educations());
        syncExperiences(resume, request.experiences());
        syncActivities(resume, request.activities());
        syncTrainings(resume, request.trainings());
        syncCertifications(resume, request.certifications());
        syncAwards(resume, request.awards());
        syncOverseasExperiences(resume, request.overseasExperiences());
        syncLanguages(resume, request.languageProficiencies());
        ResumeResponse updated = loadResponse(resume);
        if (!beforeUpdate.equals(updated)) {
            resume.touch();
        }
        return updated;
    }

    @Transactional
    public void deleteMine() {
        Resume resume = resumeRepository.findByUserIdForUpdate(currentUserId())
                .orElseThrow(() -> error(ErrorCode.RESUME_NOT_FOUND, "Resume not found"));
        resumeRepository.delete(resume);
    }

    private void saveNewChildren(Resume resume, ResumeUpsertRequest request) {
        rejectIdsOnCreate(request);
        educationRepository.saveAll(request.educations().stream().map(value -> {
            Education entity = Education.create(resume); apply(entity, value); return entity;
        }).toList());
        experienceRepository.saveAll(request.experiences().stream().map(value -> {
            Experience entity = Experience.create(resume); apply(entity, value); return entity;
        }).toList());
        activityRepository.saveAll(request.activities().stream().map(value -> {
            Activity entity = Activity.create(resume); apply(entity, value); return entity;
        }).toList());
        trainingRepository.saveAll(request.trainings().stream().map(value -> {
            Training entity = Training.create(resume); apply(entity, value); return entity;
        }).toList());
        certificationRepository.saveAll(request.certifications().stream().map(value -> {
            Certification entity = Certification.create(resume); apply(entity, value); return entity;
        }).toList());
        awardRepository.saveAll(request.awards().stream().map(value -> {
            Award entity = Award.create(resume); apply(entity, value); return entity;
        }).toList());
        overseasExperienceRepository.saveAll(request.overseasExperiences().stream().map(value -> {
            OverseasExperience entity = OverseasExperience.create(resume); apply(entity, value); return entity;
        }).toList());
        languageProficiencyRepository.saveAll(request.languageProficiencies().stream().map(value -> {
            LanguageProficiency entity = LanguageProficiency.create(resume); apply(entity, value); return entity;
        }).toList());
    }

    private void syncEducations(Resume resume, List<ResumeUpsertRequest.EducationRequest> requests) {
        SyncResult<Education> result = sync(educationRepository.findAllByResumeIdOrderById(resume.getId()),
                requests, ResumeUpsertRequest.EducationRequest::id, Education::getId,
                value -> Education.create(resume), this::apply);
        educationRepository.deleteAll(result.deleted()); educationRepository.saveAll(result.current());
    }

    private void syncExperiences(Resume resume, List<ResumeUpsertRequest.ExperienceRequest> requests) {
        SyncResult<Experience> result = sync(experienceRepository.findAllByResumeIdOrderById(resume.getId()),
                requests, ResumeUpsertRequest.ExperienceRequest::id, Experience::getId,
                value -> Experience.create(resume), this::apply);
        experienceRepository.deleteAll(result.deleted()); experienceRepository.saveAll(result.current());
    }

    private void syncActivities(Resume resume, List<ResumeUpsertRequest.ActivityRequest> requests) {
        SyncResult<Activity> result = sync(activityRepository.findAllByResumeIdOrderById(resume.getId()),
                requests, ResumeUpsertRequest.ActivityRequest::id, Activity::getId,
                value -> Activity.create(resume), this::apply);
        activityRepository.deleteAll(result.deleted()); activityRepository.saveAll(result.current());
    }

    private void syncTrainings(Resume resume, List<ResumeUpsertRequest.TrainingRequest> requests) {
        SyncResult<Training> result = sync(trainingRepository.findAllByResumeIdOrderById(resume.getId()),
                requests, ResumeUpsertRequest.TrainingRequest::id, Training::getId,
                value -> Training.create(resume), this::apply);
        trainingRepository.deleteAll(result.deleted()); trainingRepository.saveAll(result.current());
    }

    private void syncCertifications(Resume resume, List<ResumeUpsertRequest.CertificationRequest> requests) {
        SyncResult<Certification> result = sync(certificationRepository.findAllByResumeIdOrderById(resume.getId()),
                requests, ResumeUpsertRequest.CertificationRequest::id, Certification::getId,
                value -> Certification.create(resume), this::apply);
        certificationRepository.deleteAll(result.deleted()); certificationRepository.saveAll(result.current());
    }

    private void syncAwards(Resume resume, List<ResumeUpsertRequest.AwardRequest> requests) {
        SyncResult<Award> result = sync(awardRepository.findAllByResumeIdOrderById(resume.getId()),
                requests, ResumeUpsertRequest.AwardRequest::id, Award::getId,
                value -> Award.create(resume), this::apply);
        awardRepository.deleteAll(result.deleted()); awardRepository.saveAll(result.current());
    }

    private void syncOverseasExperiences(
            Resume resume, List<ResumeUpsertRequest.OverseasExperienceRequest> requests
    ) {
        SyncResult<OverseasExperience> result = sync(
                overseasExperienceRepository.findAllByResumeIdOrderById(resume.getId()), requests,
                ResumeUpsertRequest.OverseasExperienceRequest::id, OverseasExperience::getId,
                value -> OverseasExperience.create(resume), this::apply);
        overseasExperienceRepository.deleteAll(result.deleted());
        overseasExperienceRepository.saveAll(result.current());
    }

    private void syncLanguages(
            Resume resume, List<ResumeUpsertRequest.LanguageProficiencyRequest> requests
    ) {
        SyncResult<LanguageProficiency> result = sync(
                languageProficiencyRepository.findAllByResumeIdOrderById(resume.getId()), requests,
                ResumeUpsertRequest.LanguageProficiencyRequest::id, LanguageProficiency::getId,
                value -> LanguageProficiency.create(resume), this::apply);
        languageProficiencyRepository.deleteAll(result.deleted());
        languageProficiencyRepository.saveAll(result.current());
    }

    private <T, R> SyncResult<T> sync(
            List<T> existing,
            List<R> requests,
            Function<R, Long> requestId,
            Function<T, Long> entityId,
            Function<R, T> creator,
            BiConsumer<T, R> updater
    ) {
        /*
         * Q. 이 공통 동기화가 소유권 검증까지 담당하나요?
         * A. 그렇다. 현재 이력서에서 읽은 ID만 remaining에 들어 있으므로 요청 ID가
         *    여기에 없으면 다른 이력서의 ID이거나 존재하지 않는 ID로 즉시 거부한다.
         */
        Map<Long, T> remaining = new HashMap<>();
        existing.forEach(value -> remaining.put(entityId.apply(value), value));
        Set<Long> requestedIds = new HashSet<>();
        List<T> current = new ArrayList<>();
        for (R request : requests) {
            Long id = requestId.apply(request);
            T entity;
            if (id == null) {
                entity = creator.apply(request);
            } else {
                if (!requestedIds.add(id)) {
                    throw invalid("Duplicate child id is not allowed: " + id);
                }
                entity = remaining.remove(id);
                if (entity == null) {
                    throw invalid("Child id does not belong to this resume: " + id);
                }
            }
            updater.accept(entity, request);
            current.add(entity);
        }
        return new SyncResult<>(current, List.copyOf(remaining.values()));
    }

    private ResumeResponse loadResponse(Resume resume) {
        PersonalInfo personalInfo = personalInfoRepository.findByResumeId(resume.getId())
                .orElseThrow(() -> error(ErrorCode.RESUME_NOT_FOUND, "Resume personal info not found"));
        return new ResumeResponse(
                resume.getId(),
                ResumeResponse.PersonalInfoResponse.from(personalInfo),
                educationRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.EducationResponse::from).toList(),
                experienceRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.ExperienceResponse::from).toList(),
                activityRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.ActivityResponse::from).toList(),
                trainingRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.TrainingResponse::from).toList(),
                certificationRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.CertificationResponse::from).toList(),
                awardRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.AwardResponse::from).toList(),
                overseasExperienceRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.OverseasExperienceResponse::from).toList(),
                languageProficiencyRepository.findAllByResumeIdOrderById(resume.getId()).stream()
                        .map(ResumeResponse.LanguageProficiencyResponse::from).toList(),
                resume.getCreatedAt()
        );
    }

    private void validateBusinessRules(ResumeUpsertRequest request) {
        /* Bean Validation은 모양과 길이를, 이 메서드는 필드 사이의 관계를 검증한다. */
        if (request == null) {
            throw invalid("Resume request is required");
        }
        request.educations().forEach(value -> {
            validatePeriod(value.admissionDate(), value.graduationDate(), "education");
            validateGpa(value.gpa(), value.maxGpa());
        });
        request.experiences().forEach(value -> {
            validatePeriod(value.startDate(), value.endDate(), "experience");
            if (Boolean.TRUE.equals(value.isWorking()) && value.endDate() != null) {
                throw invalid("Working experience must not have an end date");
            }
        });
        request.activities().forEach(value -> validatePeriod(value.startDate(), value.endDate(), "activity"));
        request.trainings().forEach(value -> validatePeriod(value.startDate(), value.endDate(), "training"));
        request.overseasExperiences().forEach(value ->
                validatePeriod(value.startDate(), value.endDate(), "overseas experience"));
    }

    private void validatePeriod(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalid(label + " end date must not be before start date");
        }
    }

    private void validateGpa(BigDecimal gpa, BigDecimal maxGpa) {
        if (gpa != null && maxGpa != null && gpa.compareTo(maxGpa) > 0) {
            throw invalid("gpa must not exceed maxGpa");
        }
    }

    private void rejectIdsOnCreate(ResumeUpsertRequest request) {
        boolean hasId = request.educations().stream().anyMatch(value -> value.id() != null)
                || request.experiences().stream().anyMatch(value -> value.id() != null)
                || request.activities().stream().anyMatch(value -> value.id() != null)
                || request.trainings().stream().anyMatch(value -> value.id() != null)
                || request.certifications().stream().anyMatch(value -> value.id() != null)
                || request.awards().stream().anyMatch(value -> value.id() != null)
                || request.overseasExperiences().stream().anyMatch(value -> value.id() != null)
                || request.languageProficiencies().stream().anyMatch(value -> value.id() != null);
        if (hasId) {
            throw invalid("Child ids are not allowed when creating a resume");
        }
    }

    private void apply(PersonalInfo entity, ResumeUpsertRequest.PersonalInfoRequest value) {
        entity.update(value.birthDate(), value.gender(), value.email(), value.phone(),
                value.address(), value.photoUrl());
    }

    private void apply(Education entity, ResumeUpsertRequest.EducationRequest value) {
        entity.update(value.schoolType(), value.schoolName(), value.admissionDate(),
                value.graduationDate(), value.status(), value.isTransfer(), value.majorName(),
                value.gpa(), value.maxGpa(), value.otherMajors());
    }

    private void apply(Experience entity, ResumeUpsertRequest.ExperienceRequest value) {
        entity.update(value.companyName(), value.departmentName(), value.startDate(), value.endDate(),
                value.isWorking(), value.position(), value.responsibilities(), value.salary(),
                value.careerDescription());
    }

    private void apply(Activity entity, ResumeUpsertRequest.ActivityRequest value) {
        entity.update(value.activityType(), value.organization(), value.startDate(),
                value.endDate(), value.description());
    }

    private void apply(Training entity, ResumeUpsertRequest.TrainingRequest value) {
        entity.update(value.name(), value.institution(), value.startDate(), value.endDate(), value.description());
    }

    private void apply(Certification entity, ResumeUpsertRequest.CertificationRequest value) {
        entity.update(value.name(), value.issuer(), value.acquisitionDate());
    }

    private void apply(Award entity, ResumeUpsertRequest.AwardRequest value) {
        entity.update(value.name(), value.issuer(), value.awardDate(), value.description());
    }

    private void apply(OverseasExperience entity, ResumeUpsertRequest.OverseasExperienceRequest value) {
        entity.update(value.countryName(), value.startDate(), value.endDate(), value.description());
    }

    private void apply(LanguageProficiency entity, ResumeUpsertRequest.LanguageProficiencyRequest value) {
        entity.update(value.languageName(), value.proficiencyLevel());
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw invalid("Current user is required");
        }
        return userId;
    }

    private ResumeException invalid(String message) {
        return error(ErrorCode.RESUME_INVALID_REQUEST, message);
    }

    private ResumeException error(ErrorCode code, String message) {
        return new ResumeException(code, message);
    }

    private record SyncResult<T>(List<T> current, List<T> deleted) {
    }
}
