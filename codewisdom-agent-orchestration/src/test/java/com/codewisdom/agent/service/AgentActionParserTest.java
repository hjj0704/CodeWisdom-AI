package com.codewisdom.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionParserTest {

    private final AgentActionParser parser = new AgentActionParser(new ObjectMapper());

    @Test
    void stripsMarkerAndParsesJavadocAction() {
        String raw = "可以为选中方法加注释。\nAGENT_ACTIONS_JSON:[{\"type\":\"JAVADOC\",\"scope\":\"selection\",\"summary\":\"为 getUser 补充 Javadoc\"}]";
        AgentActionParser.ParseResult result = parser.parse(raw);
        assertEquals("可以为选中方法加注释。", result.visibleAnswer());
        assertEquals(1, result.actions().size());
        assertEquals("JAVADOC", result.actions().get(0).type());
        assertEquals("selection", result.actions().get(0).scope());
        assertTrue(result.actions().get(0).requiresConfirmation());
    }

    @Test
    void parsesNavigateAction() {
        String raw = "找到了。\nAGENT_ACTIONS_JSON:[{\"type\":\"NAVIGATE\",\"symbol\":\"runAudit\",\"filePath\":\"src/main/java/Foo.java\",\"summary\":\"跳转到 runAudit\"}]";
        AgentActionParser.ParseResult result = parser.parse(raw);
        assertEquals("runAudit", result.actions().get(0).symbol());
        assertEquals("src/main/java/Foo.java", result.actions().get(0).filePath());
    }

    @Test
    void parsesClarifyAction() {
        String raw = "需要确认范围。\nAGENT_ACTIONS_JSON:[{\"type\":\"CLARIFY\",\"clarifyQuestion\":\"要跳转哪一个？\",\"options\":[{\"id\":\"a\",\"label\":\"类 A\"},{\"id\":\"b\",\"label\":\"类 B\"}]}]";
        AgentActionParser.ParseResult result = parser.parse(raw);
        assertEquals("CLARIFY", result.actions().get(0).type());
        assertEquals(2, result.actions().get(0).options().size());
        assertTrue(!result.actions().get(0).requiresConfirmation());
    }

    @Test
    void ignoresUnknownActionTypes() {
        String raw = "ok\nAGENT_ACTIONS_JSON:[{\"type\":\"DELETE_FILE\",\"scope\":\"file\"}]";
        AgentActionParser.ParseResult result = parser.parse(raw);
        assertTrue(result.actions().isEmpty());
    }
}
