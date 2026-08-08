package com.cenergy.passed_backend.domain.coverletter.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CoverLetterScoreConverter implements AttributeConverter<CoverLetterScore, String> {

    @Override
    public String convertToDatabaseColumn(CoverLetterScore attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
    }

    @Override
    public CoverLetterScore convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CoverLetterScore.fromDatabaseValue(dbData);
    }
}
