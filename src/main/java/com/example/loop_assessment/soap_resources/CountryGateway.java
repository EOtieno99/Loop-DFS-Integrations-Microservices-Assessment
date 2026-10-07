package com.example.loop_assessment.soap_resources;

import com.example.loop_assessment.domain.dto.CountryData;

public interface CountryGateway {
  CountryData fetchCountryDetails(String name);
}
