package com.mintjams.social_insight.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class ApiService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final List<String> apiKeys;
    private int currentKeyIndex = 0;

    public ApiService(@Value("${api.keys}") String apiKeys) {
        this.apiKeys = Arrays.asList(apiKeys.split(","));
    }

    public String callApi(String url) {
        String response = null;
        int attempts = 0;

        while (attempts < apiKeys.size()) {
            String currentKey = getCurrentApiKey();
            try {
                // URL에 API 키를 포함하여 요청
                String fullUrl = url + "?key=" + currentKey;
                response = restTemplate.getForObject(fullUrl, String.class);

                // 요청 성공 시 결과 반환
                return response;
            } catch (RestClientException e) {
                System.out.println("API key failed: " + currentKey);
                switchToNextKey();
                attempts++;
            }
        }

        // 모든 키가 실패한 경우 예외 발생
        throw new RuntimeException("All API keys are invalid or expired.");
    }

    public String getCurrentApiKey() {
        return apiKeys.get(currentKeyIndex);
    }

    private void switchToNextKey() {
        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
    }
}
