package com.codewisdom.resource.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑（T-206）：exchange {@code cw.topic}，队列 {@code cw.parse}。
 */
@Configuration
@ConditionalOnProperty(prefix = "codewisdom.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    public static final String EXCHANGE = "cw.topic";
    public static final String PARSE_QUEUE = "cw.parse";
    public static final String PARSE_ROUTING_KEY = "parse.task";

    @Bean
    TopicExchange codewisdomTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue parseQueue() {
        return new Queue(PARSE_QUEUE, true);
    }

    @Bean
    Binding parseBinding(Queue parseQueue, TopicExchange codewisdomTopicExchange) {
        return BindingBuilder.bind(parseQueue).to(codewisdomTopicExchange).with(PARSE_ROUTING_KEY);
    }

    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
