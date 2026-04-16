package com.solv.wefin.domain.trading.dart.client;

import com.solv.wefin.domain.trading.dart.client.dto.DartCorpCodeItem;
import com.solv.wefin.domain.trading.dart.config.DartProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class DartCorpCodeClient {

    private static final String CORP_CODE_PATH = "/api/corpCode.xml";

    private final RestClient dartRestClient;
    private final DartProperties dartProperties;

    public DartCorpCodeClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                              DartProperties dartProperties) {
        this.dartRestClient = dartRestClient;
        this.dartProperties = dartProperties;
    }

    public List<DartCorpCodeItem> fetchAll() {
        byte[] zipBytes = downloadZip();
        byte[] xmlBytes = extractXml(zipBytes);
        return parseXml(xmlBytes);
    }

    private byte[] downloadZip() {
        byte[] bytes;
        try {
            bytes = dartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CORP_CODE_PATH)
                            .queryParam("crtfc_key", dartProperties.getKey())
                            .build())
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("DART corpCode.xml 다운로드 실패", e);
            throw new BusinessException(ErrorCode.DART_CORP_CODE_FETCH_FAILED);
        }

        if (bytes == null || bytes.length < 4) {
            throw new BusinessException(ErrorCode.DART_CORP_CODE_FETCH_FAILED, "빈 응답");
        }

        // ZIP 매직 넘버 'PK' (0x50 0x4B) 확인 — 아니면 DART가 XML 에러 응답을 보낸 경우
        if (bytes[0] != 0x50 || bytes[1] != 0x4B) {
            String errorDetail = extractErrorFromXml(bytes);
            log.error("DART 에러 응답 수신: {}", errorDetail);
            throw new BusinessException(ErrorCode.DART_CORP_CODE_FETCH_FAILED,
                    "DART 에러 응답: " + errorDetail);
        }

        return bytes;
    }

    private String extractErrorFromXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = doc.getDocumentElement();
            String status = textOf(root, "status");
            String message = textOf(root, "message");
            return String.format("status=%s, message=%s", status, message);
        } catch (Exception e) {
            return String.format("응답 파싱 실패 (크기: %dB)", bytes.length);
        }
    }

    private byte[] extractXml(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".xml")) {
                    return zis.readAllBytes();
                }
            }
            throw new BusinessException(ErrorCode.DART_CORP_CODE_FETCH_FAILED,
                    "DART 응답 ZIP에 XML 파일이 없습니다.");
        } catch (IOException e) {
            log.error("DART corpCode ZIP 해제 실패", e);
            throw new BusinessException(ErrorCode.DART_CORP_CODE_FETCH_FAILED,
                    "ZIP 해제 실패: " + e.getMessage());
        }
    }

    private List<DartCorpCodeItem> parseXml(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));

            NodeList lists = doc.getElementsByTagName("list");
            List<DartCorpCodeItem> items = new ArrayList<>(lists.getLength());
            for (int i = 0; i < lists.getLength(); i++) {
                Element el = (Element) lists.item(i);
                String stockCode = textOf(el, "stock_code");
                if (stockCode == null || stockCode.isBlank()) {
                    continue;
                }
                String corpCode = textOf(el, "corp_code");
                String corpName = textOf(el, "corp_name");
                String modifyDate = textOf(el, "modify_date");
                items.add(new DartCorpCodeItem(
                        stockCode.trim(),
                        corpCode == null ? null : corpCode.trim(),
                        corpName == null ? null : corpName.trim(),
                        modifyDate == null ? null : modifyDate.trim()
                ));
            }
            return items;
        } catch (Exception e) {
            log.error("DART corpCode XML 파싱 실패", e);
            throw new BusinessException(ErrorCode.DART_CORP_CODE_FETCH_FAILED,
                    "XML 파싱 실패: " + e.getMessage());
        }
    }

    private String textOf(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent();
    }
}
