package com.codewisdom.resource.mq;

public interface ParseTaskPublisherPort {

    void publish(ParseTaskMessage message);
}
