package com.example.loop_assessment.soap_resources;

import com.example.loop_assessment.domain.dto.CountryData;
import com.example.loop_assessment.domain.dto.CountryData.LanguageData;

import java.io.StringReader;
import java.util.ArrayList;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;
import org.xml.sax.InputSource;


public final class SoapXml {
    public static final String NS = "http://www.oorsprong.org/websamples.countryinfo";
    public static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";

    private SoapXml() {
    }

    public static String request(String operation, String parameter, String value) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\""
                + SOAP
                + "\"><soap:Body><"
                + operation
                + " xmlns=\""
                + NS
                + "\"><"
                + parameter
                + ">"
                + escape(value)
                + "</"
                + parameter
                + "></"
                + operation
                + "></soap:Body></soap:Envelope>";
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static Document parse(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(
                    new org.xml.sax.helpers.DefaultHandler() {
                        @Override
                        public void fatalError(org.xml.sax.SAXParseException e)
                                throws org.xml.sax.SAXException {
                            throw e;
                        }

                        @Override
                        public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                            throw e;
                        }
                    });
            var doc = builder.parse(new InputSource(new StringReader(xml)));
            if (!SOAP.equals(doc.getDocumentElement().getNamespaceURI())
                    || !"Envelope".equals(doc.getDocumentElement().getLocalName()))
                throw new IllegalArgumentException("Invalid envelope");
            if (doc.getElementsByTagNameNS(SOAP, "Fault").getLength() > 0)
                throw new UpstreamException("Country provider returned a SOAP fault", false);
            return doc;
        } catch (UpstreamException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamException("Country provider returned invalid XML", false, e);
        }
    }

    public static boolean isFault(String xml) {
        try {
            parse(xml);
            return false;
        } catch (UpstreamException e) {
            return e.getMessage().equals("Country provider returned a SOAP fault");
        }
    }

    public static Element result(Document doc, String operation) {
        var body = doc.getElementsByTagNameNS(SOAP, "Body");
        if (body.getLength() != 1) throw invalid();
        var response = child((Element) body.item(0), operation + "Response");
        return child(response, operation + "Result");
    }

    private static Element child(Element parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && NS.equals(e.getNamespaceURI()) && name.equals(e.getLocalName()))
                return e;
        throw invalid();
    }

    private static UpstreamException invalid() {
        return new UpstreamException("Country provider returned an incomplete response", false);
    }

    private static String field(Element parent, String name) {
        return child(parent, name).getTextContent().strip();
    }

    public static String isoCode(Document doc) {
        return result(doc, "CountryISOCode").getTextContent().strip();
    }

    public static CountryData country(Document doc) {
        var data = result(doc, "FullCountryInfo");
        var languages = new ArrayList<LanguageData>();
        var list = child(data, "Languages").getElementsByTagNameNS(NS, "tLanguage");
        for (int i = 0; i < list.getLength(); i++) {
            var language = (Element) list.item(i);
            if ("true"
                    .equals(language.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "nil")))
                continue;
            languages.add(new LanguageData(field(language, "sISOCode"), field(language, "sName")));
        }
        return new CountryData(
                field(data, "sISOCode"),
                field(data, "sName"),
                field(data, "sCapitalCity"),
                field(data, "sPhoneCode"),
                field(data, "sContinentCode"),
                field(data, "sCurrencyISOCode"),
                field(data, "sCountryFlag"),
                languages);
    }
}
