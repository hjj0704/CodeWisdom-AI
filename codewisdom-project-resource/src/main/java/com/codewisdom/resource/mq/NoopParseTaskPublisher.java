package com.codewisdom.resource.mq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "codewisdom.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopParseTaskPublisher implements ParseTaskPublisherPort {

    @Override
    public void publish(ParseTaskMessage message) {
        // MQ 未启用时静默跳过
    }
}
