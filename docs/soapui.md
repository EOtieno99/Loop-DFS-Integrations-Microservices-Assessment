# SoapUI setup and contract verification

1. Install SoapUI Open Source from [SmartBear](https://www.soapui.org/downloads/soapui/). Use the installer appropriate to your operating system.
2. Open SoapUI and select **File → New SOAP Project**. Name it `CountryInfoService`.
3. Set Initial WSDL to `http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL`; select Create Requests. If the public site is down, import the checked-in `src/main/resources/CountryInfoService.wsdl` instead.
4. Expand the SOAP 1.1 binding and `CountryISOCode`, open its request, and replace `?` in `sCountryName` with `Tanzania`. Send it to the service URL without `?WSDL`.
5. Copy `CountryISOCodeResult` (normally `TZ`) into `sCountryISOCode` in `FullCountryInfo` and send that request. Inspect ISO/name/capital/phone/continent/currency/flag and `Languages/tLanguage`.
6. Add both requests to a TestSuite/TestCase. Add XPath assertions for the result and use a Property Transfer from the first response's result to the second request parameter. Add a SOAP Response assertion and an SLA assertion aligned with the configured 4s request timeout.

Country ISO request:

```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:c="http://www.oorsprong.org/websamples.countryinfo">
  <soap:Body>
    <c:CountryISOCode><c:sCountryName>Tanzania</c:sCountryName></c:CountryISOCode>
  </soap:Body>
</soap:Envelope>
```

Full information request:

```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:c="http://www.oorsprong.org/websamples.countryinfo">
  <soap:Body>
    <c:FullCountryInfo><c:sCountryISOCode>TZ</c:sCountryISOCode></c:FullCountryInfo>
  </soap:Body>
</soap:Envelope>
```

Use `Content-Type: text/xml; charset=utf-8` and `SOAPAction: ""` for SOAP 1.1; the WSDL declares an empty action. CountryISOCodeResult is the exact case-sensitive response element, despite different spellings in the assessment text. The local automated client tests assert this contract. The public WSDL was retrieved during implementation; live SOAP responses are an external dependency and are not required by the test suite.
