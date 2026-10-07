package com.example.loop_assessment.domain.model;

import com.example.loop_assessment.domain.dto.CountryData;
import com.example.loop_assessment.domain.dto.CountryResponse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "countries")
public class CountryInfo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @Column(name = "iso_code", nullable = false, unique = true, length = 2)
  private String isoCode;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(name = "capital_city", nullable = false, length = 100)
  private String capitalCity;

  @Column(name = "phone_code", nullable = false, length = 20)
  private String phoneCode;

  @Column(name = "continent_code", nullable = false, length = 10)
  private String continentCode;

  @Column(name = "currency_iso_code", nullable = false, length = 3)
  private String currencyIsoCode;

  @Column(name = "country_flag", nullable = false, length = 500)
  private String countryFlag;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "country", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("isoCode ASC")
  private List<Language> languages = new ArrayList<>();

  protected CountryInfo() {}

  public CountryInfo(CountryData data) {
    replace(data);
  }

  public void replace(CountryData data) {
    updatedAt = Instant.now();
    isoCode = data.isoCode();
    name = data.name();
    capitalCity = data.capitalCity();
    phoneCode = data.phoneCode();
    continentCode = data.continentCode();
    currencyIsoCode = data.currencyIsoCode();
    countryFlag = data.countryFlag();

    languages.removeIf(
        l -> data.languages().stream().noneMatch(d -> d.isoCode().equals(l.getIsoCode())));
    data.languages()
        .forEach(
            l -> {
              languages.stream()
                  .filter(existing -> existing.getIsoCode().equals(l.isoCode()))
                  .findFirst()
                  .ifPresentOrElse(
                      existing -> existing.update(l), () -> languages.add(new Language(this, l)));
            });
  }

  @PrePersist
  void created() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getVersion() {
    return version;
  }

  public CountryResponse toResponse() {
    return new CountryResponse(
        id,
        version,
        createdAt,
        updatedAt,
        new CountryData(
            isoCode,
            name,
            capitalCity,
            phoneCode,
            continentCode,
            currencyIsoCode,
            countryFlag,
            languages.stream().map(Language::toData).toList()));
  }
}
