package com.example.loop_assessment.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class CountryNamesTest {
    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(CountryNames.convertToSentenceCase("  kENYA  ")).isEqualTo("Kenya");
        assertThat(CountryNames.convertToSentenceCase("UNITED   STATES")).isEqualTo("United states");
        assertThat(CountryNames.convertToSentenceCase("éQUATEUR")).isEqualTo("Équateur");
    }

    @Test
    void independentOfHostLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(CountryNames.convertToSentenceCase("INDIA")).isEqualTo("India");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsWhitespace() {
        assertThatIllegalArgumentException().isThrownBy(() -> CountryNames.convertToSentenceCase("  "));
    }
}
