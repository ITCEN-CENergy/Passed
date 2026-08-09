package com.cenergy.passed_backend.domain.skill.repository;

import com.cenergy.passed_backend.domain.skill.entity.UserSkillEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface UserSkillEvidenceRepository extends JpaRepository<UserSkillEvidence, Long> {

    @Query("""
            select evidence
            from UserSkillEvidence evidence
            join fetch evidence.userSkill userSkill
            join fetch userSkill.skill skill
            where userSkill.user.id = :userId
              and skill.id in :skillIds
            order by evidence.updatedAt desc, evidence.id desc
            """)
    List<UserSkillEvidence> findAllByUserIdAndSkillIds(
            @Param("userId") Long userId,
            @Param("skillIds") Collection<Long> skillIds
    );

    @Query("""
            SELECT evidence
            FROM UserSkillEvidence evidence
            WHERE evidence.userSkill.id = :userSkillId
              AND evidence.userSkill.user.id = :userId
            ORDER BY evidence.id
            """)
    List<UserSkillEvidence> findAllOwnedByUserSkillId(
            @Param("userSkillId") Long userSkillId,
            @Param("userId") Long userId
    );
}
