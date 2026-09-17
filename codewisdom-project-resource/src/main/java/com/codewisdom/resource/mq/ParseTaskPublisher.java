package com.codewisdom.resource.mq;

import com.codewisdom.resource.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "codewisdom.mq", name = "enabled", havingValue = "true")
public class ParseTaskPublisher implements ParseTaskPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ParseTaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ParseTaskMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.PARSE_ROUTING_KEY,
                message);
        log.info("已投递 cw.parse action={} projectId={} taskId={}",
                message.action(), message.projectId(), message.taskId());
    }
}
