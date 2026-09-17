package com.codewisdom.agent.service.fix;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.agent.domain.fix.FixModels.DiffRequest;
import com.codewisdom.agent.domain.fix.FixModels.FixSuggestResult;
import com.codewisdom.agent.domain.fix.FixModels.FixSuggestion;
import com.codewisdom.agent.domain.fix.FixModels.FixTargetIssue;
import com.codewisdom.agent.domain.fix.FixModels.HitlDecision;
import com.codewisdom.agent.domain.fix.FixModels.HitlReviewState;
import com.codewisdom.agent.domain.fix.FixModels.HitlReviewStatus;
import com.codewisdom.agent.domain.fix.FixModels.StructuredDiff;
import com.codewisdom.agent.dto.FixDiffRequest;
import com.codewisdom.agent.dto.FixRecordView;
import com.codewisdom.agent.dto.FixSuggestRequest;
import com.codewisdom.agent.dto.HitlStateView;
import com.codewisdom.agent.dto.HitlSubmitRequest;
import com.codewisdom.agent.dto.StructuredDiffView;
import com.codewisdom.agent.entity.FixRecordEntity;
import com.codewisdom.agent.entity.HitlReviewEntity;
import com.codewisdom.agent.mapper.FixRecordMapper;
import com.codewisdom.agent.mapper.HitlReviewMapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class FixWorkflowService {

    private final FixSuggestGenerator fixSuggestGenerator;
    private final DiffGenerator diffGenerator;
    private final HitlReviewService hitlReviewService;
    private final FixRecordMapper fixRecordMapper;
    private final HitlReviewMapper hitlReviewMapper;
    private final ObjectMapper objectMapper;

    public FixWorkflowService(FixSuggestGenerator fixSuggestGenerator,
                               DiffGenerator diffGenerator,
                               HitlReviewService hitlReviewService,
                               FixRecordMapper fixRecordMapper,
                               HitlReviewMapper hitlReviewMapper,
                               ObjectMapper objectMapper) {
        this.fixSuggestGenerator = fixSuggestGenerator;
        this.diffGenerator = diffGenerator;
        this.hitlReviewService = hitlReviewService;
        this.fixRecordMapper = fixRecordMapper;
        this.hitlReviewMapper = hitlReviewMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<FixRecordView> suggest(long projectId, FixSuggestRequest request) {
        List<FixTargetIssue> targets = request.issues().stream()
                .map(i -> new FixTargetIssue(
                        i.ruleId(), i.filePath(), i.line(), i.description(),
                        i.riskNote() == null ? "" : i.riskNote(), i.riskLevel()))
                .toList();

        FixSuggestResult result = fixSuggestGenerator.suggest(targets);
        if (result.skipped()) {
            throw BizException.of(ErrorCode.AGENT_ERROR, "未收到任何修复建议，请确认审计问题列表非空");
        }

        List<FixRecordView> saved = new ArrayList<>();
        for (FixSuggestion suggestion : result.suggestions()) {
            FixTargetIssue matched = targets.stream()
                    .filter(t -> t.issueKey().equals(suggestion.issueKey()))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                continue;
            }
            FixRecordEntity existing = fixRecordMapper.selectOne(new LambdaQueryWrapper<FixRecordEntity>()
                    .eq(FixRecordEntity::getProjectId, projectId)
                    .eq(FixRecordEntity::getIssueKey, suggestion.issueKey())
                    .orderByDesc(FixRecordEntity::getId)
                    .last("LIMIT 1"));
            if (existing != null) {
                existing.setRuleId(suggestion.ruleId());
                existing.setFilePath(matched.filePath());
                existing.setLineNo(matched.lineNo());
                existing.setRiskLevel(matched.riskLevel().name());
                existing.setSuggestion(suggestion.suggestion());
                existing.setRationale(suggestion.rationale());
                existing.setDiffChanged(false);
                fixRecordMapper.updateById(existing);
                saved.add(toView(existing));
                continue;
            }
            FixRecordEntity entity = new FixRecordEntity();
            entity.setProjectId(projectId);
            entity.setIssueKey(suggestion.issueKey());
            entity.setRuleId(suggestion.ruleId());
            entity.setFilePath(matched.filePath());
            entity.setLineNo(matched.lineNo());
            entity.setRiskLevel(matched.riskLevel().name());
            entity.setSuggestion(suggestion.suggestion());
            entity.setRationale(suggestion.rationale());
            entity.setDiffChanged(false);
            fixRecordMapper.insert(entity);
            saved.add(toView(entity));
        }
        if (saved.isEmpty()) {
            throw BizException.of(ErrorCode.AGENT_ERROR, "未能生成修复建议，请确认已运行审计且问题列表非空");
        }
        return listRecords(projectId);
    }

    public List<FixRecordView> listRecords(long projectId) {
        return fixRecordMapper.selectList(new LambdaQueryWrapper<FixRecordEntity>()
                        .eq(FixRecordEntity::getProjectId, projectId)
                        .orderByDesc(FixRecordEntity::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public StructuredDiffView diff(FixDiffRequest request) {
        StructuredDiff diff = diffGenerator.generate(new DiffRequest(
                request.filePath(), request.beforeContent(), request.afterContent()));
        if (request.fixRecordId() != null) {
            FixRecordEntity record = fixRecordMapper.selectById(request.fixRecordId());
            if (record != null) {
                try {
                    record.setDiffChanged(diff.changed());
                    record.setDiffJson(objectMapper.writeValueAsString(diff));
                    fixRecordMapper.updateById(record);
                } catch (Exception ignored) {
                    // diff 预览失败不阻断
                }
            }
        }
        return toDiffView(diff);
    }

    @Transactional
    public HitlStateView submitHitl(long projectId, HitlSubmitRequest request) {
        String sessionKey = resolveHitlSessionKey(projectId, request.sessionKey(), request.fixRecordId());

        HitlReviewState current = loadState(sessionKey);
        HitlReviewState next = hitlReviewService.submit(current, new HitlDecision(request.status()));

        HitlReviewEntity row = new HitlReviewEntity();
        row.setProjectId(projectId);
        row.setSessionKey(sessionKey);
        row.setFixRecordId(request.fixRecordId());
        row.setRound(next.round());
        row.setDecisionStatus(next.lastStatus().name());
        row.setApproved(next.approved());
        row.setIsTerminated(next.terminated());
        hitlReviewMapper.insert(row);

        return new HitlStateView(next.round(), next.approved(), next.terminated(), next.lastStatus().name());
    }

    public HitlStateView currentHitl(long projectId, String sessionKey) {
        String key = resolveHitlSessionKey(projectId, sessionKey, null);
        HitlReviewState state = loadState(key);
        return new HitlStateView(state.round(), state.approved(), state.terminated(), state.lastStatus().name());
    }

    private String resolveHitlSessionKey(long projectId, String sessionKey, Long fixRecordId) {
        if (sessionKey != null && !sessionKey.isBlank()) {
            return sessionKey;
        }
        if (fixRecordId != null) {
            return defaultSessionKey(projectId) + "-fix-" + fixRecordId;
        }
        return defaultSessionKey(projectId);
    }

    private HitlReviewState loadState(String sessionKey) {
        HitlReviewEntity latest = hitlReviewMapper.selectOne(new LambdaQueryWrapper<HitlReviewEntity>()
                .eq(HitlReviewEntity::getSessionKey, sessionKey)
                .orderByDesc(HitlReviewEntity::getRound)
                .last("LIMIT 1"));
        if (latest == null) {
            return HitlReviewState.initial();
        }
        HitlReviewStatus status;
        try {
            status = HitlReviewStatus.valueOf(latest.getDecisionStatus());
        } catch (Exception ex) {
            status = HitlReviewStatus.PENDING;
        }
        return new HitlReviewState(
                latest.getRound(),
                Boolean.TRUE.equals(latest.getApproved()),
                Boolean.TRUE.equals(latest.getIsTerminated()),
                status);
    }

    private static String defaultSessionKey(long projectId) {
        return "project-" + projectId;
    }

    private FixRecordView toView(FixRecordEntity entity) {
        return new FixRecordView(
                entity.getId(),
                entity.getIssueKey(),
                entity.getRuleId(),
                entity.getFilePath(),
                entity.getLineNo() == null ? 0 : entity.getLineNo(),
                entity.getRiskLevel(),
                entity.getSuggestion(),
                entity.getRationale(),
                Boolean.TRUE.equals(entity.getDiffChanged()),
                entity.getCreatedAt());
    }

    private StructuredDiffView toDiffView(StructuredDiff diff) {
        List<StructuredDiffView.DiffHunkView> hunks = diff.hunks().stream()
                .map(h -> new StructuredDiffView.DiffHunkView(
                        h.oldStart(), h.oldLines(), h.newStart(), h.newLines(), h.lines()))
                .toList();
        return new StructuredDiffView(
                diff.filePath(), diff.beforeContent(), diff.afterContent(), diff.changed(), hunks);
    }
}
