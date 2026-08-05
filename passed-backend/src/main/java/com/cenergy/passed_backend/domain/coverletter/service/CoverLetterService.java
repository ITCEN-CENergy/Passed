package com.cenergy.passed_backend.domain.coverletter.service;

import com.cenergy.passed_backend.domain.coverletter.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverLetterService {
    private final CoverLetterChunkRepository coverLetterChunkRepository;
    private final CoverLetterFeedbackRepository coverLetterFeedbackRepository;
    private final CoverLetterItemFeedbackRepository coverLetterItemFeedbackRepository;
    private final CoverLetterItemRepository coverLetterItemRepository;
    private final CoverLetterQuestionRepository coverLetterQuestionRepository;
    private final CoverLetterRepository coverLetterRepository;



}