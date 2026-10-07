package com.example.loop_assessment.soap_resources;

import static org.assertj.core.api.Assertions.*;

import com.example.loop_assessment.service.ServiceException;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class SoapCountryClientTest {
  HttpServer server;
  jakarta.validation.ValidatorFactory validators;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    validators = Validation.buildDefaultValidatorFactory();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    validators.close();
  }

  SoapCountryClient client(int attempts) {
    return new SoapCountryClient(
        new SoapProperties(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
            Duration.ofMillis(300),
            Duration.ofMillis(300),
            attempts,
            Duration.ofMillis(1),
            Duration.ofSeconds(30),
            2),
        new SimpleMeterRegistry(),
        validators.getValidator());
  }

  @Test
  void performsBothCallsWithCorrectParametersAndSoapAction() {
    var count = new AtomicInteger();
    server.createContext(
        "/",
        exchange -> {
          var request =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          assertThat(exchange.getRequestMethod()).isEqualTo("POST");
          assertThat(exchange.getRequestHeaders().getFirst("SOAPAction")).isEqualTo("\"\"");
          String response;
          if (count.getAndIncrement() == 0) {
            assertThat(request).contains("<sCountryName>Kenya</sCountryName>");
            response = SoapXmlTest.iso("KE");
          } else {
            assertThat(request).contains("<sCountryISOCode>KE</sCountryISOCode>");
            response = SoapXmlTest.full();
          }
          byte[] body = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    assertThat(client(1).fetchCountryDetails("Kenya"))
        .isEqualTo(com.example.loop_assessment.TestCountries.kenya());
    assertThat(count.get()).isEqualTo(2);
  }

  @Test
  void retriesTransientErrorsAndOpensCircuit() {
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        e -> {
          calls.incrementAndGet();
          e.sendResponseHeaders(503, -1);
          e.close();
        });
    server.start();
    var client = client(2);
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> client.fetchCountryDetails("Kenya")).isInstanceOf(UpstreamException.class);
    assertThat(calls.get()).isEqualTo(10);
    assertThatThrownBy(() -> client.fetchCountryDetails("Kenya")).isInstanceOf(ServiceException.class);
    assertThat(calls.get()).isEqualTo(10);
  }

  @Test
  void doesNotRetryPermanentHttpErrors() {
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        e -> {
          calls.incrementAndGet();
          e.sendResponseHeaders(400, -1);
          e.close();
        });
    server.start();
    assertThatThrownBy(() -> client(3).fetchCountryDetails("Kenya")).isInstanceOf(UpstreamException.class);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void unknownCountryStopsBeforeFullInfo() {
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        e -> {
          calls.incrementAndGet();
          var bytes = SoapXmlTest.iso("Country not found").getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    assertThatThrownBy(() -> client(3).fetchCountryDetails("Nowhere")).isInstanceOf(ServiceException.class);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void rejectsOversizedResponseWithoutRetry() {
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        e -> {
          calls.incrementAndGet();
          var bytes = "x".repeat(1_000_001).getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, bytes.length);
          try {
            e.getResponseBody().write(bytes);
          } finally {
            e.close();
          }
        });
    server.start();
    assertThatThrownBy(() -> client(3).fetchCountryDetails("Kenya"))
        .isInstanceOf(UpstreamException.class)
        .hasMessageContaining("too large");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void rejectsInconsistentCountryIso() {
    server.createContext(
        "/",
        e -> {
          var request = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          var xml =
              request.contains("<CountryISOCode")
                  ? SoapXmlTest.iso("KE")
                  : SoapXmlTest.full()
                      .replace("<sISOCode>KE</sISOCode>", "<sISOCode>TZ</sISOCode>");
          var bytes = xml.getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    assertThatThrownBy(() -> client(1).fetchCountryDetails("Kenya"))
        .isInstanceOf(UpstreamException.class)
        .hasMessageContaining("inconsistent");
  }

  @Test
  void rejectsInvalidIsoBeforeSecondCall() {
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        e -> {
          calls.incrementAndGet();
          var bytes = SoapXmlTest.iso("INVALID").getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    assertThatThrownBy(() -> client(3).fetchCountryDetails("Kenya"))
        .isInstanceOf(UpstreamException.class)
        .hasMessageContaining("invalid ISO");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void soapFaultOnHttp500IsNotRetried() {
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        e -> {
          calls.incrementAndGet();
          var bytes =
              SoapXmlTest.envelope("<s:Fault><faultstring>Provider error</faultstring></s:Fault>")
                  .getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(500, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    assertThatThrownBy(() -> client(3).fetchCountryDetails("Kenya"))
        .isInstanceOf(UpstreamException.class)
        .hasMessageContaining("SOAP fault");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void timesOutSlowProvider() {
    server.createContext(
        "/",
        e -> {
          try {
            Thread.sleep(600);
            e.sendResponseHeaders(200, -1);
          } catch (Exception ignored) {
          } finally {
            e.close();
          }
        });
    server.start();
    assertThatThrownBy(() -> client(1).fetchCountryDetails("Kenya"))
        .isInstanceOf(UpstreamException.class)
        .hasMessageContaining("could not be reached");
  }
}
