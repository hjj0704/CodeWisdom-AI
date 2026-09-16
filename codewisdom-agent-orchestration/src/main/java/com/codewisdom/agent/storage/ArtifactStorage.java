package com.codewisdom.agent.storage;

import java.util.Optional;

/** 对象存储抽象：MinIO 实现 + 测试用内存实现。 */
public interface ArtifactStorage {

    void put(String bucket, String objectKey, byte[] content, String contentType);

    Optional<byte[]> get(String bucket, String objectKey);

    boolean exists(String bucket, String objectKey);
}
