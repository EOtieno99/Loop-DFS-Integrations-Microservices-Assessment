package com.example.loop_assessment.controller;

import com.example.loop_assessment.domain.dto.CountryResponse;
import com.example.loop_assessment.domain.dto.CreateCountryRequest;
import com.example.loop_assessment.domain.dto.UpdateCountryRequest;
import com.example.loop_assessment.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

  private final CountryService countryService;
  private final CountryOperationsService countryOperationsService;

  public CountryController(CountryService countryService, CountryOperationsService countryOperationsService) {
    this.countryService = countryService;
    this.countryOperationsService = countryOperationsService;
  }

  @PostMapping
  public ResponseEntity<CountryResponse> create(@Valid @RequestBody CreateCountryRequest request) {
    var result = countryService.createCountry(request.name());
    var builder =
        result.created()
            ? ResponseEntity.created(URI.create("/api/v1/countries/" + result.country().id()))
            : ResponseEntity.ok();
    return builder.header("X-Data-Stale", Boolean.toString(result.stale())).body(result.country());
  }

  @GetMapping
  public CountryOperationsService.CountryPage listCountries(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return countryOperationsService.listCountries(page, size);
  }

  @GetMapping("/{id}")
  public CountryResponse getCountryById(@PathVariable @Positive long id) {
    return countryOperationsService.getCountry(id);
  }

  @PutMapping("/{id}")
  public CountryResponse updateCountry(
      @PathVariable @Positive long id, @Valid @RequestBody UpdateCountryRequest request) {
    return countryOperationsService.updateCountry(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteCountry(@PathVariable @Positive long id) {
    countryOperationsService.deleteCountry(id);
    return ResponseEntity.noContent().build();
  }
}
