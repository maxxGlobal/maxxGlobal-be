package com.maxx_global.service;

import com.maxx_global.dto.productPrice.CurrencyRate;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class TcmbService {
    private final WebClient webClient = WebClient.create();
    private volatile List<CurrencyRate> latestRates = new ArrayList<>();

    private static final String TCMB_URL = "https://www.tcmb.gov.tr/kurlar/today.xml";

    @PostConstruct
    public void init() {
        fetchAndParse();
    }

    @Scheduled(cron = "0 5 9 * * ?") // her gün 09:05
    public void scheduledFetch() {
        fetchAndParse();
    }

    public List<CurrencyRate> getLatestRates() {
        // sadece USD ve EUR dön
        return latestRates.stream()
                .filter(r -> "USD".equalsIgnoreCase(r.code()) || "EUR".equalsIgnoreCase(r.code()))
                .toList();
    }

    private void fetchAndParse() {
        try {
            String xml = webClient.get()
                    .uri(TCMB_URL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (xml == null || xml.isBlank()) return;

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList currencyNodes = (NodeList) xpath.evaluate("/Tarih_Date/Currency", doc, XPathConstants.NODESET);

            List<CurrencyRate> rates = new ArrayList<>();
            for (int i = 0; i < currencyNodes.getLength(); i++) {
                String code = xpath.evaluate("@CurrencyCode", currencyNodes.item(i));
                String name = xpath.evaluate("Isim", currencyNodes.item(i));

                String forexBuyingStr = xpath.evaluate("ForexBuying", currencyNodes.item(i));
                String forexSellingStr = xpath.evaluate("ForexSelling", currencyNodes.item(i));
                String banknoteBuyingStr = xpath.evaluate("BanknoteBuying", currencyNodes.item(i));
                String banknoteSellingStr = xpath.evaluate("BanknoteSelling", currencyNodes.item(i));

                Double forexBuying = parseDoubleOrNull(forexBuyingStr);
                Double forexSelling = parseDoubleOrNull(forexSellingStr);
                Double banknoteBuying = parseDoubleOrNull(banknoteBuyingStr);
                Double banknoteSelling = parseDoubleOrNull(banknoteSellingStr);

                rates.add(new CurrencyRate(code, name, forexBuying, forexSelling, banknoteBuying, banknoteSelling));
            }

            this.latestRates = rates;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Double parseDoubleOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception ex) {
            return null;
        }
    }
}
