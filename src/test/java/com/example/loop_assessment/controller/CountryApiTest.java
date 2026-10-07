package com.example.loop_assessment.controller;

import static com.example.loop_assessment.TestCountries.kenya;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.loop_assessment.domain.dto.CountryData;
import com.example.loop_assessment.domain.dto.UpdateCountryRequest;
import com.example.loop_assessment.repository.CountryRepository;
import com.example.loop_assessment.soap_resources.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CountryApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    CountryRepository repository;
    @MockitoBean
    CountryGateway gateway;

    @BeforeEach
    void resetData() {
        repository.deleteAll();
        reset(gateway);
    }

    long create() throws Exception {
        when(gateway.fetchCountryDetails("Kenya")).thenReturn(kenya());
        var result =
                mvc.perform(
                                post("/api/v1/countries")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"  kENYA \"}"))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("X-Data-Stale", "false"))
                        .andExpect(header().exists("Location"))
                        .andExpect(jsonPath("$.country.languages.length()").value(2))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void fullCrudPersistsLanguagesAndCascadesDelete() throws Exception {
        long id = create();
        mvc.perform(get("/api/v1/countries/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country.isoCode").value("KE"));
        mvc.perform(get("/api/v1/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        var changed =
                new CountryData(
                        "KE",
                        "Kenya",
                        "Mombasa",
                        "254",
                        "AF",
                        "KES",
                        "",
                        java.util.List.of(new CountryData.LanguageData("eng", "Updated English")));
        mvc.perform(
                        put("/api/v1/countries/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new UpdateCountryRequest(0L, changed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.country.capitalCity").value("Mombasa"))
                .andExpect(jsonPath("$.country.languages.length()").value(1))
                .andExpect(jsonPath("$.country.languages[0].name").value("Updated English"));
        mvc.perform(delete("/api/v1/countries/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/countries/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUNTRY_NOT_FOUND"));
    }

    @Test
    void languageOnlyUpdateIncrementsAggregateVersion() throws Exception {
        long id = create();
        var original = kenya();
        var changed =
                new CountryData(
                        original.isoCode(),
                        original.name(),
                        original.capitalCity(),
                        original.phoneCode(),
                        original.continentCode(),
                        original.currencyIsoCode(),
                        original.countryFlag(),
                        java.util.List.of(new CountryData.LanguageData("eng", "English")));
        mvc.perform(
                        put("/api/v1/countries/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new UpdateCountryRequest(0L, changed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(
                        put("/api/v1/countries/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new UpdateCountryRequest(0L, original))))
                .andExpect(status().isConflict());
    }

    @Test
    void staleVersionIsConflict() throws Exception {
        long id = create();
        mvc.perform(
                        put("/api/v1/countries/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new UpdateCountryRequest(99L, kenya()))))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateCountryIsConflict() throws Exception {
        create();
        mvc.perform(
                        post("/api/v1/countries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankMissingAndMalformedNames() throws Exception {
        for (String body : java.util.List.of("{}", "{\"name\":\"  \"}", "{\"name\":null}", "invalid")) {
            mvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsInvalidPagingAndIds() throws Exception {
        mvc.perform(get("/api/v1/countries?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/countries?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/countries/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/countries/not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateLanguages() throws Exception {
        long id = create();
        var bad =
                new CountryData(
                        "KE",
                        "Kenya",
                        "Nairobi",
                        "254",
                        "AF",
                        "KES",
                        "",
                        java.util.List.of(
                                new CountryData.LanguageData("eng", "English"),
                                new CountryData.LanguageData("eng", "Duplicate")));
        mvc.perform(
                        put("/api/v1/countries/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new UpdateCountryRequest(0L, bad))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LANGUAGE"));
    }

    @Test
    void outageWithoutFallbackReturns503() throws Exception {
        when(gateway.fetchCountryDetails("Kenya")).thenThrow(new UpstreamException("timeout", true));
        mvc.perform(
                        post("/api/v1/countries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void outageReturnsStoredCountryWithStaleHeader() throws Exception {
        long id = create();
        when(gateway.fetchCountryDetails("Kenya")).thenThrow(new UpstreamException("timeout", true));
        mvc.perform(
                        post("/api/v1/countries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Stale", "true"))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void invalidProviderResponseReturns502() throws Exception {
        when(gateway.fetchCountryDetails("Kenya")).thenThrow(new UpstreamException("bad XML", false));
        mvc.perform(
                        post("/api/v1/countries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Country provider is unavailable or returned an invalid response; try again"
                                                + " later"));
    }

    @Test
    void unknownCountryReturns404() throws Exception {
        when(gateway.fetchCountryDetails("Nowhere"))
                .thenThrow(
                        new com.example.loop_assessment.service.ServiceException(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                "COUNTRY_UNKNOWN",
                                "Unknown country"));
        mvc.perform(
                        post("/api/v1/countries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Nowhere\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRoutesMethodsAndContentTypesUseProperStatus() throws Exception {
        mvc.perform(get("/api/v1/unknown")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/countries")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/countries").contentType(MediaType.TEXT_PLAIN).content("Kenya"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/v1/countries").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void requestIdIsPreservedOrSafelyReplaced() throws Exception {
        mvc.perform(get("/api/v1/countries").header("X-Request-ID", "test-123"))
                .andExpect(header().string("X-Request-ID", "test-123"));
        mvc.perform(get("/api/v1/countries").header("X-Request-ID", "<unsafe>"))
                .andExpect(
                        header().string("X-Request-ID", org.hamcrest.Matchers.matchesPattern("[a-f0-9-]{36}")));
    }
}
