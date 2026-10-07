package com.example.loop_assessment.service;

import com.example.loop_assessment.domain.dto.UpdateCountryRequest;
import com.example.loop_assessment.domain.model.CountryInfo;
import com.example.loop_assessment.domain.dto.CountryData;
import com.example.loop_assessment.domain.dto.CountryResponse;
import com.example.loop_assessment.repository.CountryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CountryOperationsService {
  private final CountryRepository countryRepository;

  public CountryOperationsService(CountryRepository countryRepository) {
    this.countryRepository = countryRepository;
  }

  public Optional<CountryResponse> findCountryByName(String name) {
    return countryRepository.findByNameIgnoreCase(name).map(CountryInfo::toResponse);
  }

  public CountryResponse getCountry(long id) {
    return requiredCountry(id).toResponse();
  }

  public CountryPage listCountries(int page, int size) {
    var result = countryRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
    return new CountryPage(
        result.getContent().stream().map(CountryInfo::toResponse).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional
  public CountryResponse createCountry(CountryData data) {
    validateLanguages(data);
    if (countryRepository.existsByIsoCode(data.isoCode()))
      throw conflict("Country ISO code already exists");
    return countryRepository.saveAndFlush(new CountryInfo(data)).toResponse();
  }

  @Transactional
  public CountryResponse updateCountry(long id, UpdateCountryRequest request) {
    var country = requiredCountry(id);
    if (!country.getVersion().equals(request.version()))
      throw conflict("Country was modified; fetch the latest version and retry");
    validateLanguages(request.country());
    country.replace(request.country());
    return countryRepository.saveAndFlush(country).toResponse();
  }

  @Transactional
  public void deleteCountry(long id) {
    countryRepository.delete(requiredCountry(id));
    countryRepository.flush();
  }

  private CountryInfo requiredCountry(long id) {
    return countryRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ServiceException(
                    HttpStatus.NOT_FOUND, "COUNTRY_NOT_FOUND", "Country " + id + " was not found"));
  }

  private void validateLanguages(CountryData data) {
    if (data.languages().stream().map(CountryData.LanguageData::isoCode).distinct().count()
        != data.languages().size())
      throw new ServiceException(
          HttpStatus.BAD_REQUEST,
          "DUPLICATE_LANGUAGE",
          "Language ISO codes must be unique within a country");
  }

  private ServiceException conflict(String message) {
    return new ServiceException(HttpStatus.CONFLICT, "COUNTRY_CONFLICT", message);
  }

  public record CountryPage(
      List<CountryResponse> content, int page, int size, long totalElements, int totalPages) {}
}
