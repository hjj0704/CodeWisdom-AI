package com.codewisdom.resource.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "codewisdom.minio", name = "enabled", havingValue = "true")
public class MinioSourceArchiveStorage implements SourceArchiveStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioSourceArchiveStorage.class);

    private final MinioClient client;

    public MinioSourceArchiveStorage(MinioClient minioClient) {
        this.client = minioClient;
    }

    @Override
    public void putFile(String bucket, String objectKey, Path file, String contentType) {
        try {
            ensureBucket(bucket);
            long size = Files.size(file);
            try (InputStream in = Files.newInputStream(file)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(in, size, -1)
                        .contentType(contentType == null ? "application/octet-stream" : contentType)
                        .build());
            }
            log.info("MinIO 归档完成 bucket={} key={} size={}", bucket, objectKey, size);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 归档失败: " + bucket + "/" + objectKey, ex);
        }
    }

    @Override
    public long objectSize(String bucket, String objectKey) {
        try {
            return client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build()).size();
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) {
                return -1L;
            }
            throw new IllegalStateException("MinIO stat 失败: " + bucket + "/" + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO stat 失败: " + bucket + "/" + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        return objectSize(bucket, objectKey) >= 0;
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO 桶已创建: {}", bucket);
        }
    }
}
