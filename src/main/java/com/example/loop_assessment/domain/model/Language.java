package com.example.loop_assessment.domain.model;

import com.example.loop_assessment.domain.dto.CountryData.LanguageData;
import jakarta.persistence.*;

@Entity
@Table(name = "languages",
    uniqueConstraints = @UniqueConstraint(columnNames = {"country_id", "iso_code"}))
public class Language {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "iso_code", nullable = false, length = 10)
  private String isoCode;

  @Column(nullable = false, length = 100)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_id")
  private CountryInfo country;

  protected Language() {}

  public Language(CountryInfo country, LanguageData data) {
    this.country = country;
    this.isoCode = data.isoCode();
    this.name = data.name();
  }

  public String getIsoCode() {
    return isoCode;
  }

  public void update(LanguageData data) {
    name = data.name();
  }

  public LanguageData toData() {
    return new LanguageData(isoCode, name);
  }
}
