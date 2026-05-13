package com.suno.mall.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "provider.logistics", name = "mode", havingValue = "real")
public class RealLogisticsProvider implements LogisticsProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${provider.logistics.endpoint:}")
    private String endpoint;

    @Value("${provider.logistics.api-key:}")
    private String apiKey;

    @Override
    public String createTrackingNo(String recycleOrderNo) {
        return "TRK-" + recycleOrderNo;
    }

    @Override
    public String queryStatus(String trackingNo) {
        if (endpoint.isBlank() || apiKey.isBlank()) {
            throw new IllegalArgumentException("物流平台未配置 endpoint/api-key");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                "trackingNo", trackingNo,
                "apiKey", apiKey
        ), headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
        if (response == null || response.get("status") == null) {
            return "UNKNOWN";
        }
        return String.valueOf(response.get("status"));
    }
}
