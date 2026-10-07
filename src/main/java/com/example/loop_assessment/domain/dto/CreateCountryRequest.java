package com.example.loop_assessment.domain.dto;

import jakarta.validation.constraints.*;

public record CreateCountryRequest(@NotBlank String name) {}
