package com.codewisdom.agent.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 测试与无 MinIO 环境下的内存对象存储。 */
public final class InMemoryArtifactStorage implements ArtifactStorage {

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

    @Override
    public void put(String bucket, String objectKey, byte[] content, String contentType) {
        buckets.computeIfAbsent(bucket, key -> new ConcurrentHashMap<>())
                .put(objectKey, content.clone());
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        Map<String, byte[]> objects = buckets.get(bucket);
        if (objects == null) {
            return Optional.empty();
        }
        byte[] data = objects.get(objectKey);
        return data == null ? Optional.empty() : Optional.of(data.clone());
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        Map<String, byte[]> objects = buckets.get(bucket);
        return objects != null && objects.containsKey(objectKey);
    }
}
