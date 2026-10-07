package com.example.loop_assessment.soap_resources;

import jakarta.validation.constraints.*;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("country.soap")
public record SoapProperties(
        @NotNull
        URI endpoint,
        @NotNull
        Duration connectTimeout,
        @NotNull
        Duration requestTimeout,
        @Min(1) @Max(5)
        int maxAttempts,
        @NotNull
        Duration retryWait,
        @NotNull
        Duration openWait,
        @Min(1)
        int maxConcurrentCalls) {

    public SoapProperties {
        if (endpoint != null &&
                !("http".equals(endpoint.getScheme())
                        || "https".equals(endpoint.getScheme())))
            throw new IllegalArgumentException("SOAP endpoint must be HTTP(S)");

        if (connectTimeout != null && (connectTimeout.isZero()
                || connectTimeout.isNegative())
                || requestTimeout != null && (requestTimeout.isZero()
                || requestTimeout.isNegative())
                || retryWait != null && retryWait.isNegative()
                || openWait != null && (openWait.isZero() || openWait.isNegative()))
            throw new IllegalArgumentException(
                    "SOAP timeouts must be positive and retry wait nonnegative");
    }
}
