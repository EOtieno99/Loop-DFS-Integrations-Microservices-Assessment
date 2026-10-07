package com.example.loop_assessment.repository;

import com.example.loop_assessment.domain.model.CountryInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<CountryInfo, Long> {
    Optional<CountryInfo> findByNameIgnoreCase(String name);
    boolean existsByIsoCode(String isoCode);
}
