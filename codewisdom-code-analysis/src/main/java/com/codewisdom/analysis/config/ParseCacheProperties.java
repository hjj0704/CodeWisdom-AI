package com.codewisdom.analysis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.parse-cache")
public class ParseCacheProperties {

    /** 是否启用 Redis 缓存；false 时使用进程内缓存（测试档）。 */
    private boolean redisEnabled = false;

    /** 缓存 TTL 秒数。 */
    private long ttlSeconds = 3600;

    /** 并行分片线程数。 */
    private int parallelism = 4;
}
