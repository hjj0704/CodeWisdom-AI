package com.codewisdom.agent.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.agent.dto.ChatMessageView;
import com.codewisdom.agent.dto.ChatReplyView;
import com.codewisdom.agent.dto.ChatRequest;
import com.codewisdom.agent.dto.ChatSessionView;
import com.codewisdom.agent.dto.CreateSessionRequest;
import com.codewisdom.agent.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/projects/{projectId}/sessions")
    public R<List<ChatSessionView>> listSessions(@PathVariable long projectId) {
        return R.ok(chatService.listSessions(projectId));
    }

    @PostMapping("/projects/{projectId}/sessions")
    public R<ChatSessionView> createSession(@PathVariable long projectId,
                                              @RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        return R.ok(chatService.createSession(projectId, title));
    }

    @PostMapping("/projects/{projectId}/onboard")
    public R<ChatReplyView> onboard(@PathVariable long projectId,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(chatService.onboard(projectId, authorization));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatMessageView>> messages(@PathVariable long sessionId) {
        return R.ok(chatService.listMessages(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/chat")
    public R<ChatReplyView> chat(@PathVariable long sessionId,
                                 @Valid @RequestBody ChatRequest request,
                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(chatService.chat(sessionId, request.content(), authorization));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(@PathVariable long sessionId) {
        chatService.deleteSession(sessionId);
        return R.ok(null);
    }
}
