package com.example.loop_assessment.soap_resources;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SoapXmlTest {
    static String envelope(String body) {
        return "<s:Envelope xmlns:s='" + SoapXml.SOAP + "'><s:Body>" + body + "</s:Body></s:Envelope>";
    }

    static String iso(String value) {
        return envelope(
                "<m:CountryISOCodeResponse xmlns:m='"
                        + SoapXml.NS
                        + "'><m:CountryISOCodeResult>"
                        + value
                        + "</m:CountryISOCodeResult></m:CountryISOCodeResponse>");
    }

    static String full() {
        return envelope(
                """
                        <FullCountryInfoResponse xmlns="http://www.oorsprong.org/websamples.countryinfo">
                          <FullCountryInfoResult><sISOCode>KE</sISOCode><sName>Kenya</sName>
                          <sCapitalCity>Nairobi</sCapitalCity><sPhoneCode>254</sPhoneCode><sContinentCode>AF</sContinentCode>
                          <sCurrencyISOCode>KES</sCurrencyISOCode><sCountryFlag>http://example.com/ke.png</sCountryFlag>
                          <Languages><tLanguage><sISOCode>eng</sISOCode><sName>English</sName></tLanguage>
                          <tLanguage><sISOCode>swa</sISOCode><sName>Swahili</sName></tLanguage></Languages>
                          </FullCountryInfoResult></FullCountryInfoResponse>
                        """);
    }

    @Test
    void extractsNamespacedIso() {
        assertThat(SoapXml.isoCode(SoapXml.parse(iso("KE")))).isEqualTo("KE");
    }

    @Test
    void extractsAllFieldsAndLanguages() {
        assertThat(SoapXml.country(SoapXml.parse(full())))
                .isEqualTo(com.example.loop_assessment.TestCountries.kenya());
    }

    @Test
    void escapesRequestText() {
        var xml = SoapXml.request("CountryISOCode", "sCountryName", "A & B < C ' D");
        assertThat(xml).contains("A &amp; B &lt; C &apos; D");
        assertThat(
                SoapXml.parse(xml)
                        .getElementsByTagNameNS(SoapXml.NS, "sCountryName")
                        .item(0)
                        .getTextContent())
                .isEqualTo("A & B < C ' D");
    }

    @Test
    void rejectsSoapFault() {
        assertThatThrownBy(
                () -> SoapXml.parse(envelope("<s:Fault><faultstring>secret</faultstring></s:Fault>")))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("SOAP fault");
    }

    @Test
    void rejectsMalformedXmlAndMissingFields() {
        assertThatThrownBy(() -> SoapXml.parse("<broken")).isInstanceOf(UpstreamException.class);
        assertThatThrownBy(
                () -> SoapXml.country(SoapXml.parse(full().replace("<sName>Kenya</sName>", ""))))
                .isInstanceOf(UpstreamException.class);
    }

    @Test
    void rejectsExternalEntities() {
        String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>" + iso("&xxe;");
        assertThatThrownBy(() -> SoapXml.parse(xml)).isInstanceOf(UpstreamException.class);
    }

    @Test
    void rejectsWrongNamespaces() {
        assertThatThrownBy(
                () -> SoapXml.isoCode(SoapXml.parse(iso("KE").replace(SoapXml.NS, "urn:wrong"))))
                .isInstanceOf(UpstreamException.class);
    }

    @Test
    void supportsEmptyLanguageList() {
        String xml = full().replaceAll("(?s)<Languages>.*?</Languages>", "<Languages/>");
        assertThat(SoapXml.country(SoapXml.parse(xml)).languages()).isEmpty();
    }
}
