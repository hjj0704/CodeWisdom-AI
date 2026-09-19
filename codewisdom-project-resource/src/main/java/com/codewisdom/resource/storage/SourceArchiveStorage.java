package com.codewisdom.resource.storage;

import java.nio.file.Path;

/**
 * 源码归档存储（T-205）：将导入产物写入 {@code cw-source} 桶。
 */
public interface SourceArchiveStorage {

    void putFile(String bucket, String objectKey, Path file, String contentType);

    long objectSize(String bucket, String objectKey);

    boolean exists(String bucket, String objectKey);
}
