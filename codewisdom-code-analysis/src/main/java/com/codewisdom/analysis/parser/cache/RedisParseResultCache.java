package com.codewisdom.analysis.parser.cache;

import com.codewisdom.analysis.config.ParseCacheProperties;
import com.codewisdom.analysis.parser.SourceStructure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "codewisdom.parse-cache", name = "redis-enabled", havingValue = "true")
public class RedisParseResultCache implements ParseResultCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisParseResultCache(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 ParseCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(properties.getTtlSeconds());
    }

    @Override
    public Optional<SourceStructure> get(String cacheKey) {
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SourceStructure.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String cacheKey, SourceStructure structure) {
        try {
            String json = objectMapper.writeValueAsString(structure);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("解析结果序列化失败", ex);
        }
    }
}
