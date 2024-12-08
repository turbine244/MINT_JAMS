package com.mintjams.social_insight.service;

import com.google.gson.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class ApiService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final List<String> apiKeys;
    private int currentKeyIndex = 0;

    public ApiService(@Value("${api.keys}") String apiKeys) {
        // 쉼표로 구분된 API 키 리스트를 초기화
        this.apiKeys = Arrays.stream(apiKeys.split(","))
                .map(String::trim) // 공백 제거
                .filter(key -> key.matches("AIza[\\w-]+")) // 유효한 API 키만 필터링
                .toList();

        // 유효한 키가 없으면 예외 발생
        if (this.apiKeys.isEmpty()) {
            throw new IllegalStateException("No valid API keys found in configuration!");
        }

        // 초기화된 API 키 출력
        this.apiKeys.forEach(key -> System.out.println("Loaded API key: " + key));
    }

    public String callApi(String channelTitle) {
        int attempts = 0;

        while (attempts < apiKeys.size()) {
            String currentKey = getCurrentApiKey();
            try {
                // 유효성 검사용 URL 생성
                String url = UriComponentsBuilder.fromHttpUrl("https://www.googleapis.com/youtube/v3/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "channel")
                        .queryParam("q", "test") // 임의의 유효 쿼리
                        .queryParam("key", currentKey)
                        .toUriString();

                System.out.println("Validating API key with URL: " + url);

                // API 호출
                String response = restTemplate.getForObject(url, String.class);

                // 응답 데이터 유효성 검사
                if (isValidResponse(response)) {
                    System.out.println("Valid API key found: " + currentKey);
                    return currentKey; // 유효한 API 키 반환
                }
            } catch (RestClientException e) {
                System.err.println("[RestClientException] API key failed: " + currentKey + ". Reason: " + e.getMessage());
            } catch (RuntimeException e) {
                System.err.println("[RuntimeException] API key failed: " + currentKey + ". Reason: " + e.getMessage());
            }

            // 다음 키로 전환
            switchToNextKey();
            attempts++;
        }

        throw new RuntimeException("No valid API keys available.");
    }

    private String getCurrentApiKey() {
        if (currentKeyIndex >= apiKeys.size()) {
            throw new IllegalStateException("Invalid API key index: " + currentKeyIndex);
        }
        String currentKey = apiKeys.get(currentKeyIndex);
        System.out.println("Current API key: " + currentKey); // 디버깅 로그 추가
        return currentKey;
    }

    private void switchToNextKey() {
        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
        System.out.println("Switched to API key: " + getCurrentApiKey());
    }

    private boolean isValidResponse(String response) {
        // 응답이 "error"로 시작하면 유효하지 않은 코드로 간주
        if (response.trim().startsWith("{\"error")) {
            System.err.println("Invalid API key: response contains 'error'.");
            return false;
        }
        // 그렇지 않으면 유효한 응답
        System.out.println("Valid API key: response is valid.");
        return true;
    }

}