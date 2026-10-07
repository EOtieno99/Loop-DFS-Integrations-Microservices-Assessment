package com.example.loop_assessment.domain.dto;

import java.time.Instant;

public record CountryResponse(
        Long id,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        CountryData country) {
}
