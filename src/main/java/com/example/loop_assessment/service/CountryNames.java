package com.example.loop_assessment.service;

import java.util.Locale;

public final class CountryNames {

    private CountryNames() {
    }

    public static String convertToSentenceCase(String name) {

        String value = name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);

        if (value.isEmpty()) throw new IllegalArgumentException("Country name must not be blank");
        int first = value.offsetByCodePoints(0, 1);

        return value.substring(0, first).toUpperCase(Locale.ROOT) + value.substring(first);
    }
}
