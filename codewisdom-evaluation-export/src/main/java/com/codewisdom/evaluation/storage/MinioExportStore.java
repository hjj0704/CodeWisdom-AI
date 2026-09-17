package com.codewisdom.evaluation.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

/** MinIO 实现 {@link ExportStorage.Store}，桶 {@code cw-export}（T-803）。 */
@Component
@ConditionalOnProperty(prefix = "codewisdom.minio", name = "enabled", havingValue = "true")
public class MinioExportStore implements ExportStorage.Store {

    private static final Logger log = LoggerFactory.getLogger(MinioExportStore.class);

    private final MinioClient client;

    public MinioExportStore(MinioClient minioClient) {
        this.client = minioClient;
    }

    @Override
    public void put(String bucket, String objectKey, byte[] content) {
        try {
            ensureBucket(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("application/zip")
                    .build());
            log.info("导出 ZIP 已上传 bucket={} key={} size={}", bucket, objectKey, content.length);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 上传失败: " + bucket + "/" + objectKey, ex);
        }
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return Optional.of(in.readAllBytes());
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IllegalStateException("MinIO 下载失败: " + bucket + "/" + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 下载失败: " + bucket + "/" + objectKey, ex);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO 桶已创建: {}", bucket);
        }
    }
}
