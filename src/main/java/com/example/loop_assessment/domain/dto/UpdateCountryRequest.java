package com.example.loop_assessment.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateCountryRequest(
    @NotNull @PositiveOrZero Long version, @NotNull @Valid CountryData country) {}
