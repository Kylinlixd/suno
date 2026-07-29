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
@ConditionalOnProperty(prefix = "provider.image-audit", name = "mode", havingValue = "real")
public class RealBaiduImageAuditProvider implements ImageAuditProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${provider.image-audit.baidu.endpoint:}")
    private String endpoint;

    @Value("${provider.image-audit.baidu.access-token:}")
    private String accessToken;

    @Override
    public boolean pass(String imageUrl) {
        if (endpoint.isBlank() || accessToken.isBlank()) {
            throw new IllegalArgumentException("百度AI审核未配置 endpoint/access-token");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                "imageUrl", imageUrl,
                "accessToken", accessToken
        ), headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
        if (response == null) {
            return false;
        }
        Object pass = response.get("pass");
        return pass instanceof Boolean && (Boolean) pass;
    }
}
