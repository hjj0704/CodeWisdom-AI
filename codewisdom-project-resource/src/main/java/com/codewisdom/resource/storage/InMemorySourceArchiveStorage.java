package com.codewisdom.resource.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 测试 / 无 MinIO 环境下的内存实现。 */
@Component
@ConditionalOnProperty(prefix = "codewisdom.minio", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySourceArchiveStorage implements SourceArchiveStorage {

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

    @Override
    public void putFile(String bucket, String objectKey, Path file, String contentType) {
        try {
            byte[] data = Files.readAllBytes(file);
            buckets.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>()).put(objectKey, data);
        } catch (IOException ex) {
            throw new IllegalStateException("读取归档文件失败: " + file, ex);
        }
    }

    @Override
    public long objectSize(String bucket, String objectKey) {
        Map<String, byte[]> objects = buckets.get(bucket);
        if (objects == null) {
            return -1L;
        }
        byte[] data = objects.get(objectKey);
        return data == null ? -1L : data.length;
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        return objectSize(bucket, objectKey) >= 0;
    }
}
