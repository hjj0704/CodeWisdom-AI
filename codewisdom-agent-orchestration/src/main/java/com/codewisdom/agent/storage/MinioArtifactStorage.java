package com.codewisdom.agent.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * MinIO 对象存储实现（local 档启用）。
 *
 * <p>桶 {@code cw-artifact} 存放生成的文档产物（见 architecture.md）。
 */
@Component
@ConditionalOnProperty(prefix = "codewisdom.minio", name = "enabled", havingValue = "true")
public class MinioArtifactStorage implements ArtifactStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioArtifactStorage.class);

    private final MinioClient client;

    public MinioArtifactStorage(MinioClient minioClient) {
        this.client = minioClient;
    }

    @Override
    public void put(String bucket, String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 上传失败: " + bucket + "/" + objectKey, ex);
        }
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        try {
            try (InputStream stream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())) {
                return Optional.of(stream.readAllBytes());
            }
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IllegalStateException("MinIO 下载失败: " + bucket + "/" + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 下载失败: " + bucket + "/" + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) {
                return false;
            }
            throw new IllegalStateException("MinIO stat 失败: " + bucket + "/" + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO stat 失败: " + bucket + "/" + objectKey, ex);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO 桶已创建: {}", bucket);
        }
    }
}
