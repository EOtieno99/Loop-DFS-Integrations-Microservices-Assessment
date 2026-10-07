package com.example.loop_assessment.service;

import com.example.loop_assessment.domain.dto.CountryResponse;
import com.example.loop_assessment.soap_resources.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class CountryService {
  private final CountryGateway gateway;
  private final CountryOperationsService countryOperationsService;
  // Store metrics for counters and timers, it helps to see how often uses can see fallBack data for example
  private final MeterRegistry metrics;

  public CountryService(CountryGateway gateway, CountryOperationsService countryOperationsService, MeterRegistry metrics) {
    this.gateway = gateway;
    this.countryOperationsService = countryOperationsService;
    this.metrics = metrics;
  }

  public CountryImportResult createCountry(String countryName) {
    String normalizedCountryName = CountryNames.convertToSentenceCase(countryName);
    try {

      return new CountryImportResult(countryOperationsService.createCountry(
              gateway.fetchCountryDetails(normalizedCountryName)),
              true, false);

    } catch (UpstreamException e) {

      return fallback(normalizedCountryName, e);

    } catch (ServiceException e) {
      if (e.status() != org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE) throw e;
      return fallback(normalizedCountryName, e);
    }
  }

  private CountryImportResult fallback(String name, RuntimeException cause) {
    var cached = countryOperationsService.findCountryByName(name);
    if (cached.isPresent()) {
      metrics.counter("country.import.fallback").increment();
      return new CountryImportResult(cached.get(), false, true);
    }
    throw cause;
  }

  public record CountryImportResult(CountryResponse country, boolean created, boolean stale) {}
}
