package com.example.loop_assessment.soap_resources;

import com.example.loop_assessment.domain.dto.CountryData;
import com.example.loop_assessment.service.ServiceException;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.micrometer.core.instrument.*;
import jakarta.validation.Validator;

import java.io.IOException;
import java.net.http.*;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SoapCountryClient implements CountryGateway {
    private static final Logger log = LoggerFactory.getLogger(SoapCountryClient.class);
    private final SoapProperties properties;
    private final HttpClient http;
    private final CircuitBreaker circuit;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final MeterRegistry metrics;
    private final Validator validator;

    public SoapCountryClient(SoapProperties properties, MeterRegistry metrics, Validator validator) {
        this.properties = properties;
        this.metrics = metrics;
        this.validator = validator;
        http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        circuit =
                CircuitBreaker.of(
                        "countrySoap",
                        CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .minimumNumberOfCalls(5)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(properties.openWait())
                                .ignoreExceptions(ServiceException.class)
                                .build());
        retry =
                Retry.of(
                        "countrySoap",
                        RetryConfig.custom()
                                .maxAttempts(properties.maxAttempts())
                                .intervalFunction(
                                        io.github.resilience4j.core.IntervalFunction.ofExponentialRandomBackoff(
                                                properties.retryWait().toMillis(), 2, 0.3))
                                .retryOnException(
                                        e -> e instanceof UpstreamException upstream && upstream.retryable())
                                .build());
        bulkhead =
                Bulkhead.of(
                        "countrySoap",
                        BulkheadConfig.custom()
                                .maxConcurrentCalls(properties.maxConcurrentCalls())
                                .maxWaitDuration(java.time.Duration.ZERO)
                                .build());
        metrics.gauge("country.soap.circuit.state", circuit, c -> c.getState().getOrder());
        metrics.gauge(
                "country.soap.available.permits",
                bulkhead,
                b -> b.getMetrics().getAvailableConcurrentCalls());
        retry.getEventPublisher().onRetry(e -> metrics.counter("country.soap.retries").increment());
    }

    @Override
    public CountryData fetchCountryDetails(String name) {
        Supplier<CountryData> call = () -> lookup(name);
        call = Retry.decorateSupplier(retry, call);
        call = CircuitBreaker.decorateSupplier(circuit, call);
        call = Bulkhead.decorateSupplier(bulkhead, call);
        try {
            return call.get();
        } catch (CallNotPermittedException | BulkheadFullException e) {
            throw new ServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_UNAVAILABLE",
                    "Country provider is temporarily unavailable; try again later");
        }
    }

    private CountryData lookup(String name) {
        String iso = SoapXml.isoCode(SoapXml.parse(send("CountryISOCode", "sCountryName", name)));
        if (iso.isBlank() || iso.equalsIgnoreCase("Country not found"))
            throw new ServiceException(
                    HttpStatus.NOT_FOUND,
                    "COUNTRY_UNKNOWN",
                    "The country provider could not find that country");
        if (!iso.matches("[A-Z]{2}"))
            throw new UpstreamException("Country provider returned an invalid ISO code", false);
        CountryData data =
                SoapXml.country(SoapXml.parse(send("FullCountryInfo", "sCountryISOCode", iso)));
        if (!iso.equals(data.isoCode())
                || !validator.validate(data).isEmpty()
                || data.languages().stream().map(CountryData.LanguageData::isoCode).distinct().count()
                != data.languages().size())
            throw new UpstreamException("Country provider returned inconsistent country data", false);
        return data;
    }

    private String send(String operation, String parameter, String value) {
        var sample = Timer.start(metrics);
        String outcome = "error";
        try {
            var request =
                    HttpRequest.newBuilder(properties.endpoint())
                            .timeout(properties.requestTimeout())
                            .header("Content-Type", "text/xml; charset=utf-8")
                            .header("SOAPAction", "\"\"")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(SoapXml.request(operation, parameter, value)))
                            .build();
            var response = http.send(request, new LimitedBodyHandler(1_000_000));
            if (response.statusCode() != 200) {
                if (SoapXml.isFault(response.body()))
                    throw new UpstreamException("Country provider returned a SOAP fault", false);
                boolean transientFailure = response.statusCode() == 429 || response.statusCode() >= 500;
                throw new UpstreamException(
                        "Country provider returned HTTP " + response.statusCode(), transientFailure);
            }
            outcome = "success";
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("Country lookup was interrupted", false, e);
        } catch (IOException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause())
                if (cause instanceof UpstreamException upstream) throw upstream;
            throw new UpstreamException("Country provider could not be reached", true, e);
        } finally {
            sample.stop(
                    metrics.timer("country.soap.request", "operation", operation, "outcome", outcome));
            log.atInfo()
                    .addKeyValue("operation", operation)
                    .addKeyValue("outcome", outcome)
                    .log("SOAP request completed");
        }
    }
}
