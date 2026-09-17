package com.codewisdom.agent.controller;

import com.codewisdom.agent.dto.FixDiffRequest;
import com.codewisdom.agent.dto.FixRecordView;
import com.codewisdom.agent.dto.FixSuggestRequest;
import com.codewisdom.agent.dto.HitlStateView;
import com.codewisdom.agent.dto.HitlSubmitRequest;
import com.codewisdom.agent.dto.StructuredDiffView;
import com.codewisdom.agent.service.fix.FixWorkflowService;
import com.codewisdom.common.api.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FixController {

    private final FixWorkflowService fixWorkflowService;

    public FixController(FixWorkflowService fixWorkflowService) {
        this.fixWorkflowService = fixWorkflowService;
    }

    @PostMapping("/projects/{projectId}/fix/suggest")
    public R<List<FixRecordView>> suggest(@PathVariable long projectId,
                                          @Valid @RequestBody FixSuggestRequest request) {
        return R.ok(fixWorkflowService.suggest(projectId, request));
    }

    @GetMapping("/projects/{projectId}/fix/records")
    public R<List<FixRecordView>> records(@PathVariable long projectId) {
        return R.ok(fixWorkflowService.listRecords(projectId));
    }

    @PostMapping("/projects/{projectId}/fix/diff")
    public R<StructuredDiffView> diff(@PathVariable long projectId,
                                      @Valid @RequestBody FixDiffRequest request) {
        return R.ok(fixWorkflowService.diff(request));
    }

    @GetMapping("/projects/{projectId}/fix/hitl")
    public R<HitlStateView> hitlState(@PathVariable long projectId,
                                      @RequestParam(required = false) String sessionKey) {
        return R.ok(fixWorkflowService.currentHitl(projectId, sessionKey));
    }

    @PostMapping("/projects/{projectId}/fix/hitl")
    public R<HitlStateView> submitHitl(@PathVariable long projectId,
                                      @Valid @RequestBody HitlSubmitRequest request) {
        return R.ok(fixWorkflowService.submitHitl(projectId, request));
    }
}
