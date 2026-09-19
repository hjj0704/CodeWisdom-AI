package com.codewisdom.agent.service;

import com.codewisdom.agent.dto.AgentActionView;
import com.codewisdom.agent.dto.ClarifyOptionView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AgentActionParser {

    private static final String MARKER = "AGENT_ACTIONS_JSON:";
    private static final int MAX_ACTIONS = 3;
    private static final int MAX_CLARIFY_OPTIONS = 4;
    private static final Set<String> ALLOWED_TYPES = Set.of("JAVADOC", "NAVIGATE", "CLARIFY");
    private static final Set<String> ALLOWED_SCOPES = Set.of("file", "viewport", "selection");

    private final ObjectMapper objectMapper;

    public AgentActionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParseResult parse(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return new ParseResult("", List.of());
        }
        int idx = rawAnswer.lastIndexOf(MARKER);
        if (idx < 0) {
            return new ParseResult(rawAnswer.trim(), List.of());
        }
        String visible = rawAnswer.substring(0, idx).trim();
        String jsonPart = rawAnswer.substring(idx + MARKER.length()).trim();
        List<AgentActionView> actions = parseActions(jsonPart);
        return new ParseResult(visible, actions);
    }

    private List<AgentActionView> parseActions(String jsonPart) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(jsonPart, new TypeReference<>() {
            });
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<AgentActionView> out = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                if (out.size() >= MAX_ACTIONS) {
                    break;
                }
                AgentActionView action = toAction(item);
                if (action != null) {
                    out.add(action);
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private AgentActionView toAction(Map<String, Object> item) {
        String type = asString(item.get("type")).toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            return null;
        }
        String summary = trimSummary(asString(item.get("summary")));
        return switch (type) {
            case "JAVADOC" -> parseJavadoc(item, summary);
            case "NAVIGATE" -> parseNavigate(item, summary);
            case "CLARIFY" -> parseClarify(item, summary);
            default -> null;
        };
    }

    private static AgentActionView parseJavadoc(Map<String, Object> item, String summary) {
        String scope = asString(item.get("scope")).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCOPES.contains(scope)) {
            scope = "file";
        }
        if (summary.isBlank()) {
            summary = "补充 Javadoc 注释";
        }
        return new AgentActionView(
                "JAVADOC", scope, summary, true,
                null, null, null, null, null);
    }

    private static AgentActionView parseNavigate(Map<String, Object> item, String summary) {
        String filePath = asString(item.get("filePath"));
        String symbol = asString(item.get("symbol"));
        if (filePath.isBlank() && symbol.isBlank()) {
            return null;
        }
        Integer line = parseLine(item.get("line"));
        if (summary.isBlank()) {
            summary = symbol.isBlank()
                    ? "打开文件 " + shortenPath(filePath)
                    : "跳转到 " + symbol;
        }
        return new AgentActionView(
                "NAVIGATE", "", summary, true,
                filePath.isBlank() ? null : filePath,
                symbol.isBlank() ? null : symbol,
                line, null, null);
    }

    private static AgentActionView parseClarify(Map<String, Object> item, String summary) {
        String question = asString(item.get("clarifyQuestion"));
        if (question.isBlank()) {
            question = summary.isBlank() ? "请补充一下你的需求" : summary;
        }
        List<ClarifyOptionView> options = parseOptions(item.get("options"));
        if (options.size() < 2) {
            return null;
        }
        return new AgentActionView(
                "CLARIFY", "", summary.isBlank() ? question : summary, false,
                null, null, null, question, options);
    }

    @SuppressWarnings("unchecked")
    private static List<ClarifyOptionView> parseOptions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ClarifyOptionView> out = new ArrayList<>();
        for (Object entry : list) {
            if (out.size() >= MAX_CLARIFY_OPTIONS) {
                break;
            }
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String id = asString(map.get("id"));
            String label = asString(map.get("label"));
            if (id.isBlank() || label.isBlank()) {
                continue;
            }
            if (label.length() > 80) {
                label = label.substring(0, 80) + "…";
            }
            out.add(new ClarifyOptionView(id, label));
        }
        return out;
    }

    private static Integer parseLine(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            int line = number.intValue();
            return line > 0 ? line : null;
        }
        try {
            int line = Integer.parseInt(String.valueOf(raw).trim());
            return line > 0 ? line : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trimSummary(String summary) {
        if (summary.isBlank()) {
            return "";
        }
        return summary.length() > 120 ? summary.substring(0, 120) + "…" : summary;
    }

    private static String shortenPath(String path) {
        if (path.length() <= 48) {
            return path;
        }
        return "…" + path.substring(path.length() - 45);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ParseResult(String visibleAnswer, List<AgentActionView> actions) {
    }
}
