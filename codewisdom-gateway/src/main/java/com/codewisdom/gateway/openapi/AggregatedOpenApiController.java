package com.codewisdom.gateway.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * T-104：网关统一 OpenAPI 文档入口。
 */
@RestController
public class AggregatedOpenApiController {

    private final OpenApiAggregationService aggregationService;

    public AggregatedOpenApiController(OpenApiAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JsonNode> aggregatedApiDocs() {
        return aggregationService.aggregate();
    }
}
