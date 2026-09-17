package com.codewisdom.gateway.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * T-104：拉取各下游 /v3/api-docs 并合并 paths / tags。
 */
@Service
public class OpenApiAggregationService {

    private static final List<DownstreamOpenApi> SOURCES = List.of(
            new DownstreamOpenApi("project-resource", "codewisdom-project-resource", 8081, "/api/project-resource"),
            new DownstreamOpenApi("code-analysis", "codewisdom-code-analysis", 8082, "/api/code-analysis"),
            new DownstreamOpenApi("agent-orchestration", "codewisdom-agent-orchestration", 8083, "/api/agent-orchestration"),
            new DownstreamOpenApi("evaluation-export", "codewisdom-evaluation-export", 8084, "/api/evaluation-export"));

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${codewisdom.gateway.openapi.use-discovery:false}")
    private boolean useDiscovery;

    public OpenApiAggregationService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public Mono<JsonNode> aggregate() {
        return Flux.fromIterable(SOURCES)
                .flatMap(this::fetchDoc)
                .collectList()
                .map(this::merge);
    }

    private Mono<JsonNode> fetchDoc(DownstreamOpenApi source) {
        String uri = useDiscovery
                ? "http://" + source.serviceName() + "/v3/api-docs"
                : "http://127.0.0.1:" + source.port() + "/v3/api-docs";
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(doc -> prefixPaths(doc, source.apiPrefix(), source.displayName()))
                .onErrorResume(ex -> Mono.empty());
    }

    private JsonNode prefixPaths(JsonNode doc, String apiPrefix, String serviceTag) {
        ObjectNode root = doc.deepCopy();
        JsonNode paths = root.get("paths");
        if (paths != null && paths.isObject()) {
            ObjectNode prefixed = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = paths.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String path = entry.getKey().startsWith("/") ? apiPrefix + entry.getKey() : apiPrefix + "/" + entry.getKey();
                prefixed.set(path, entry.getValue());
            }
            root.set("paths", prefixed);
        }
        ArrayNode tags = objectMapper.createArrayNode();
        ObjectNode serviceTagNode = objectMapper.createObjectNode();
        serviceTagNode.put("name", serviceTag);
        serviceTagNode.put("description", "Service: " + serviceTag);
        tags.add(serviceTagNode);
        if (root.has("tags") && root.get("tags").isArray()) {
            root.get("tags").forEach(tags::add);
        }
        root.set("tags", tags);
        root.put("openapi", root.path("openapi").asText("3.0.1"));
        ObjectNode info = objectMapper.createObjectNode();
        info.put("title", "CodeWisdom API (Aggregated)");
        info.put("version", "1.0.0");
        root.set("info", info);
        return root;
    }

    private JsonNode merge(List<JsonNode> docs) {
        ObjectNode merged = objectMapper.createObjectNode();
        merged.put("openapi", "3.0.1");
        ObjectNode info = objectMapper.createObjectNode();
        info.put("title", "CodeWisdom API (Aggregated)");
        info.put("version", "1.0.0");
        merged.set("info", info);

        ObjectNode paths = objectMapper.createObjectNode();
        ArrayNode tags = objectMapper.createArrayNode();
        for (JsonNode doc : docs) {
            JsonNode docPaths = doc.get("paths");
            if (docPaths != null && docPaths.isObject()) {
                docPaths.fields().forEachRemaining(e -> paths.set(e.getKey(), e.getValue()));
            }
            JsonNode docTags = doc.get("tags");
            if (docTags != null && docTags.isArray()) {
                docTags.forEach(tags::add);
            }
        }
        merged.set("paths", paths);
        merged.set("tags", tags);
        return merged;
    }

    private record DownstreamOpenApi(String displayName, String serviceName, int port, String apiPrefix) {
    }
}
