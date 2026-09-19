package com.codewisdom.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.entity.AuditIssueEntity;
import com.codewisdom.analysis.mapper.AuditIssueMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditIssuePersistenceService {

    private final AuditIssueMapper auditIssueMapper;

    public AuditIssuePersistenceService(AuditIssueMapper auditIssueMapper) {
        this.auditIssueMapper = auditIssueMapper;
    }

    @Transactional
    public void replaceProjectIssues(long projectId, List<AuditIssue> issues) {
        auditIssueMapper.delete(new LambdaQueryWrapper<AuditIssueEntity>()
                .eq(AuditIssueEntity::getProjectId, projectId));
        for (AuditIssue issue : issues) {
            AuditIssueEntity row = new AuditIssueEntity();
            row.setProjectId(projectId);
            row.setRuleId(clip(issue.ruleId(), 64));
            row.setCategory(issue.category().name());
            row.setRiskLevel(issue.riskLevel().name());
            row.setFilePath(clip(issue.filePath(), 1024));
            row.setLineNo(issue.line());
            row.setDescription(clip(issue.description(), 512));
            row.setTriggerSnippet(clip(issue.trigger(), 1024));
            row.setRiskDescription(clip(issue.riskDescription(), 1024));
            auditIssueMapper.insert(row);
        }
    }

    private static String clip(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
