package com.configurator.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Converter
public class LocalDateTimeAttributeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter SQLITE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime value) {
        return value == null ? null : value.format(ISO);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Legacy rows written by the SQLite dialect as epoch millis.
        if (dbData.chars().allMatch(Character::isDigit)) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(dbData)),
                    ZoneOffset.UTC);
        }
        if (dbData.contains("T")) {
            return LocalDateTime.parse(dbData, ISO);
        }
        return LocalDateTime.parse(dbData, SQLITE);
    }
}
