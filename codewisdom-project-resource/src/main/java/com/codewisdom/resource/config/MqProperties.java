package com.codewisdom.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.mq")
public class MqProperties {

    /** 是否启用 RabbitMQ（关闭时发布与消费者均为空操作）。 */
    private boolean enabled = false;

    /** 大仓库异步导入：接口立即返回 PENDING，由 {@code cw.parse} 消费执行。 */
    private boolean asyncImportEnabled = false;
}
