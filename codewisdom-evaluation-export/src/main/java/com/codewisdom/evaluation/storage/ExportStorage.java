package com.codewisdom.evaluation.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 导出对象存储（测试用内存实现；local 档可换 MinIO）。 */
public final class ExportStorage {

    private ExportStorage() {
    }

    public interface Store {
        void put(String bucket, String objectKey, byte[] content);

        Optional<byte[]> get(String bucket, String objectKey);
    }

    public static final class InMemoryStore implements Store {

        private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

        @Override
        public void put(String bucket, String objectKey, byte[] content) {
            buckets.computeIfAbsent(bucket, b -> new ConcurrentHashMap<>()).put(objectKey, content.clone());
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
    }
}
