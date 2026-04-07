package com.chuka.irir.service;

import com.chuka.irir.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeminiClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final GeminiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiClient(GeminiProperties properties, RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    public Optional<String> generateContent(String prompt) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            return Optional.empty();
        }

        String model = properties.getModel();
        if (!StringUtils.hasText(model)) {
            return Optional.empty();
        }
        model = model.trim();
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }

        String url = BASE_URL + model + ":generateContent?key=" + properties.getApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
        ));
        body.put("generationConfig", Map.of(
                "temperature", properties.getTemperature(),
                "maxOutputTokens", properties.getMaxOutputTokens()
        ));

        try {
            String response = restTemplate.postForObject(url, body, String.class);
            if (!StringUtils.hasText(response)) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                String text = textNode != null ? textNode.asText() : null;
                return Optional.ofNullable(text);
            }
        } catch (Exception e) {
            logger.warn("Gemini API call failed: {}", e.getMessage());
        }

        return Optional.empty();
    }
}
