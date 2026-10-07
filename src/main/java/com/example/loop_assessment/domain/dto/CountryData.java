package com.example.loop_assessment.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;


public record CountryData(
        @NotBlank @Pattern(regexp = "[A-Z]{2}")
        String isoCode,
        @NotBlank @Size(max = 100)
        String name,
        @NotNull @Size(max = 100)
        String capitalCity,
        @NotNull @Size(max = 20)
        String phoneCode,
        @NotNull @Size(max = 10)
        String continentCode,
        @NotNull @Size(max = 3)
        String currencyIsoCode,
        @NotNull @Size(max = 500)
        String countryFlag,

        @NotNull @Size(max = 100) List<@NotNull @Valid LanguageData> languages) {
    public record LanguageData(
            @NotBlank @Size(max = 10) String isoCode,
            @NotBlank @Size(max = 100) String name) {
    }
}
