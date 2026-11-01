package com.suno.mall.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ISBN 扫码服务：调用 Open Library 公开 API 获取书籍元数据，
 * 根据出版年份与磨损评分估算回收价格。API 不可用时允许手动填写。
 */
@Service
public class BookScanService {

    private static final Logger log = LoggerFactory.getLogger(BookScanService.class);
    private static final String OPENLIBRARY_API = "https://openlibrary.org/api/books";
    private static final Pattern ISBN_PATTERN = Pattern.compile("^(?:ISBN:)?(\\d{10}|\\d{13})$");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper;

    public BookScanService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 校验 ISBN 格式（10 或 13 位数字） */
    public static boolean isValidIsbn(String isbn) {
        Matcher matcher = ISBN_PATTERN.matcher(isbn == null ? "" : isbn.trim());
        return matcher.matches();
    }

    /**
     * 扫码获取书籍信息并估价。
     * @return 包含 title/author/publisher/publishYear/estimatedPrice/grade 的 Map
     */
    public Map<String, Object> scanByIsbn(String isbn) {
        String normalized = isbn == null ? "" : isbn.trim().toUpperCase();
        if (!isValidIsbn(normalized)) {
            throw new BizException("ISBN 格式不正确，请输入 10 或 13 位数字", ErrorCode.PARAM_INVALID);
        }

        JsonNode response = fetchFromApi(normalized);
        if (response == null || response.isEmpty()) {
            throw new BizException("未找到该 ISBN 对应的书籍，请手动填写", ErrorCode.NOT_FOUND);
        }

        String key = "ISBN:" + normalized;
        JsonNode book = response.get(key);
        if (book == null || book.isNull()) {
            throw new BizException("未找到该 ISBN 对应的书籍，请手动填写", ErrorCode.NOT_FOUND);
        }

        String title = book.path("title").asText("未知书名");
        List<String> authors = new ArrayList<>();
        JsonNode authorsNode = book.path("authors");
        if (authorsNode.isArray()) {
            for (JsonNode author : authorsNode) {
                String name = author.path("name").asText("");
                if (!name.isEmpty()) authors.add(name);
            }
        }
        String authorStr = authors.isEmpty() ? "" : String.join(", ", authors);

        List<String> publishers = new ArrayList<>();
        JsonNode publishersNode = book.path("publishers");
        if (publishersNode.isArray()) {
            for (JsonNode publisher : publishersNode) {
                String name = publisher.path("name").asText("");
                if (!name.isEmpty()) publishers.add(name);
            }
        }
        String publisherStr = publishers.isEmpty() ? "" : publishers.get(0);

        String publishDate = book.path("publish_date").asText("");
        Integer publishYear = parseYear(publishDate);

        return Map.of(
                "barcode", normalized,
                "title", title,
                "author", authorStr,
                "publisher", publisherStr,
                "publishYear", publishYear == null ? 0 : publishYear
        );
    }

    /**
     * 根据书籍信息与磨损评分估算回收价格。
     * 基准价按出版年份分档，成色系数按磨损评分线性递减。
     */
    public Map<String, Object> estimatePrice(Integer publishYear, int wearScore) {
        BigDecimal base;
        Integer year = publishYear == null ? 2020 : publishYear;
        if (year >= 2020) {
            base = new BigDecimal("25.00");
        } else if (year >= 2010) {
            base = new BigDecimal("30.00");
        } else if (year >= 2000) {
            base = new BigDecimal("40.00");
        } else if (year >= 1990) {
            base = new BigDecimal("50.00");
        } else {
            base = new BigDecimal("60.00");
        }

        // 成色系数：wearScore 0-100 → 0.2-0.8（最低 20%，最高 80% 基准价）
        double ratio = 0.2 + (Math.min(100, Math.max(0, wearScore)) / 100.0) * 0.6;
        BigDecimal estimated = base.multiply(BigDecimal.valueOf(ratio))
                .setScale(2, RoundingMode.HALF_UP);

        String grade = wearScore >= 80 ? "GOOD" : (wearScore >= 60 ? "MEDIUM" : "UNQUALIFIED");

        return Map.of(
                "estimatedPrice", estimated,
                "grade", grade
        );
    }

    private JsonNode fetchFromApi(String isbn) {
        try {
            String url = OPENLIBRARY_API + "?bibkeys=ISBN:" + isbn + "&format=json";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Open Library API returned {} for ISBN {}", response.statusCode(), isbn);
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("Open Library API call failed for ISBN {}: {}", isbn, e.getMessage());
            return null;
        }
    }

    private static Integer parseYear(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        Matcher matcher = Pattern.compile("(\\d{4})").matcher(dateStr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
