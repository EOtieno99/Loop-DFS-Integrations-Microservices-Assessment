package com.example.loop_assessment.service;

import static com.example.loop_assessment.TestCountries.kenya;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.loop_assessment.domain.dto.CountryResponse;
import com.example.loop_assessment.soap_resources.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Optional;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;

class CountryServiceTest {
    CountryGateway gateway = mock(CountryGateway.class);
    CountryOperationsService store = mock(CountryOperationsService.class);
    SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    CountryService service = new CountryService(gateway, store, metrics);
    CountryResponse saved = new CountryResponse(1L, 0L, null, null, kenya());

    @Test
    void normalizesFetchesAndSaves() {
        when(gateway.fetchCountryDetails("Kenya")).thenReturn(kenya());
        when(store.createCountry(kenya())).thenReturn(saved);
        assertThat(service.createCountry("  KENYA "))
                .isEqualTo(new CountryService.CountryImportResult(saved, true, false));
        verify(gateway).fetchCountryDetails("Kenya");
        verify(store).createCountry(kenya());
    }

    @Test
    void providerFailureUsesExplicitStaleFallback() {
        when(gateway.fetchCountryDetails("Kenya")).thenThrow(new UpstreamException("timeout", true));
        when(store.findCountryByName("Kenya")).thenReturn(Optional.of(saved));
        assertThat(service.createCountry("kenya"))
                .isEqualTo(new CountryService.CountryImportResult(saved, false, true));
        assertThat(metrics.counter("country.import.fallback").count()).isEqualTo(1);
        verify(store, never()).createCountry(any());
    }

    @Test
    void propagatesFailureWithoutStoredData() {
        var failure = new UpstreamException("timeout", true);
        when(gateway.fetchCountryDetails("Kenya")).thenThrow(failure);
        when(store.findCountryByName("Kenya")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createCountry("kenya")).isSameAs(failure);
    }

    @Test
    void openCircuitAlsoFallsBack() {
        when(gateway.fetchCountryDetails("Kenya"))
                .thenThrow(new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "OPEN", "open"));
        when(store.findCountryByName("Kenya")).thenReturn(Optional.of(saved));
        assertThat(service.createCountry("kenya").stale()).isTrue();
    }

    @Test
    void unknownCountryDoesNotUseFallback() {
        when(gateway.fetchCountryDetails("Kenya"))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "UNKNOWN", "unknown"));
        assertThatThrownBy(() -> service.createCountry("kenya")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(store);
    }

    @Test
    void databaseConflictIsNotMaskedAsFallback() {
        when(gateway.fetchCountryDetails("Kenya")).thenReturn(kenya());
        when(store.createCountry(kenya()))
                .thenThrow(new ServiceException(HttpStatus.CONFLICT, "EXISTS", "exists"));
        assertThatThrownBy(() -> service.createCountry("kenya")).isInstanceOf(ServiceException.class);
        verify(store, never()).findCountryByName(any());
    }
}
