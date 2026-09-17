package com.codewisdom.resource.mq;

import com.codewisdom.resource.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "codewisdom.mq", name = "enabled", havingValue = "true")
public class ParseTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskConsumer.class);

    private final ImportService importService;

    public ParseTaskConsumer(@Lazy ImportService importService) {
        this.importService = importService;
    }

    @RabbitListener(queues = com.codewisdom.resource.config.RabbitMqConfig.PARSE_QUEUE)
    public void onMessage(ParseTaskMessage message) {
        if (message == null || message.action() == null) {
            return;
        }
        if (ParseTaskMessage.ACTION_GIT_IMPORT.equals(message.action())) {
            importService.processAsyncGitImport(message);
            return;
        }
        if (ParseTaskMessage.ACTION_PARSE_NOTIFY.equals(message.action())) {
            log.info("收到解析通知 projectId={} taskId={}", message.projectId(), message.taskId());
        }
    }
}
