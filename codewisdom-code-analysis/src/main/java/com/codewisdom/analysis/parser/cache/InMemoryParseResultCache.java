package com.codewisdom.analysis.parser.cache;

import com.codewisdom.analysis.parser.SourceStructure;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "codewisdom.parse-cache", name = "redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryParseResultCache implements ParseResultCache {

    private final Map<String, SourceStructure> store = new ConcurrentHashMap<>();

    @Override
    public Optional<SourceStructure> get(String cacheKey) {
        return Optional.ofNullable(store.get(cacheKey));
    }

    @Override
    public void put(String cacheKey, SourceStructure structure) {
        store.put(cacheKey, structure);
    }
}
