package com.cenergy.passed_backend.global.error;

public enum ErrorCode {
    SKILL_GAP_INVALID_RESPONSE,
    SKILL_GAP_NOT_FOUND,
    SKILL_GAP_AI_TIMEOUT,
    SKILL_GAP_AI_UNAVAILABLE,
    ROADMAP_AI_TIMEOUT,
    ROADMAP_AI_UNAVAILABLE,
    ROADMAP_AI_INVALID_RESPONSE,
    ROADMAP_INVALID_REQUEST,
    ROADMAP_NOT_FOUND,
    ROADMAP_ALREADY_EXISTS,
    ROADMAP_GENERATION_IN_PROGRESS,
    ROADMAP_GENERATION_CONFLICT,
    MILESTONE_NOT_FOUND,
    ROADMAP_NO_COMPETENCY_TO_LEARN,
    ROADMAP_GENERATION_FAILED,
    RECOMMENDATION_INVALID_REQUEST,
    RECOMMENDATION_USER_NOT_FOUND,
    RECOMMENDATION_ALREADY_PROCESSING,
    RECOMMENDATION_POLICY_NOT_FOUND,
    RECOMMENDATION_POLICY_CONFIGURATION_INVALID,
    RECOMMENDATION_USER_SKILLS_NOT_FOUND,
    RECOMMENDATION_SKILL_DATA_INVALID,

    USER_SKILL_INVALID_REQUEST,
    USER_SKILL_NOT_FOUND,
    USER_SKILL_INSUFFICIENT,
    USER_SKILL_PREFERENCE_CONFLICT,
    USER_SKILL_AI_TIMEOUT,
    USER_SKILL_AI_UNAVAILABLE,
    USER_SKILL_AI_INVALID_RESPONSE,

    /** Covers malformed cover-letter commands and unavailable current-user context. */
    COVER_LETTER_INVALID_REQUEST,
    /** Indicates that an owned company cover letter could not be found. */
    COVER_LETTER_NOT_FOUND,
    /** Indicates that an owned company cover-letter item could not be found. */
    COVER_LETTER_ITEM_NOT_FOUND,
    /** Indicates that a requested item feedback does not exist for the current user. */
    COVER_LETTER_ITEM_FEEDBACK_NOT_FOUND,
    /** Indicates that AI feedback requires a nonblank answer. */
    COVER_LETTER_ITEM_ANSWER_REQUIRED,
    /** Indicates that a feedback target changed while feedback was being generated. */
    COVER_LETTER_ITEM_CHANGED,
    /** Indicates a uniqueness or concurrent-update conflict in the cover-letter domain. */
    COVER_LETTER_FEEDBACK_CONFLICT,
    /** Indicates that the current user record required for a command is missing. */
    COVER_LETTER_USER_NOT_FOUND,
    /** Indicates that the target job posting does not exist. */
    COVER_LETTER_JOB_POSTING_NOT_FOUND,
    /** Indicates duplicate company cover-letter creation for one user and job posting. */
    COVER_LETTER_ALREADY_EXISTS,
    /** Indicates duplicate display order inside one company cover letter. */
    COVER_LETTER_DISPLAY_ORDER_CONFLICT,
    /** Indicates a timeout while calling the external cover-letter AI service. */
    COVER_LETTER_AI_TIMEOUT,
    /** Indicates an unavailable external cover-letter AI service. */
    COVER_LETTER_AI_UNAVAILABLE,
    /** Indicates that the external cover-letter AI returned data outside the expected contract. */
    COVER_LETTER_AI_INVALID_RESPONSE
}
