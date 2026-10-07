package com.example.loop_assessment;

import com.example.loop_assessment.domain.dto.CountryData;
import java.util.List;

public final class TestCountries {
  private TestCountries() {}

  public static CountryData kenya() {
    return new CountryData(
        "KE",
        "Kenya",
        "Nairobi",
        "254",
        "AF",
        "KES",
        "http://example.com/ke.png",
        List.of(
            new CountryData.LanguageData("eng", "English"),
            new CountryData.LanguageData("swa", "Swahili")));
  }
}
