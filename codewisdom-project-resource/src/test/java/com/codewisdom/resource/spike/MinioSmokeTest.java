package com.codewisdom.resource.spike;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-008 S5 冒烟：MinIO 建桶 / 传 / 下 / 删。
 *
 * <p>需设置环境变量 {@code CW_MINIO_ENDPOINT} 指向可达的 MinIO（如阿里云 ECS）。
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "codewisdom.minio.enabled=true",
        "codewisdom.minio.endpoint=${CW_MINIO_ENDPOINT}",
        "codewisdom.minio.access-key=${CW_MINIO_USER:codewisdom}",
        "codewisdom.minio.secret-key=${CW_MINIO_PASSWORD:codewisdom123}"
})
@EnabledIfEnvironmentVariable(named = "CW_MINIO_ENDPOINT", matches = ".+")
@DisplayName("T-008 MinIO 冒烟")
class MinioSmokeTest {

    @Autowired
    private MinioClient minioClient;

    @Test
    @DisplayName("建桶、上传、下载、删除四步全通")
    void putGetDeleteRoundTrip() throws Exception {
        String bucket = "cw-smoke";
        String key = "smoke-" + UUID.randomUUID() + ".txt";
        byte[] data = "codewisdom-minio-smoke".getBytes(StandardCharsets.UTF_8);

        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType("text/plain")
                .build());

        long size = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()).size();
        assertThat(size).isEqualTo(data.length);

        byte[] downloaded;
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            downloaded = stream.readAllBytes();
        }
        assertThat(downloaded).isEqualTo(data);

        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        assertThat(minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())).isTrue();
    }
}
