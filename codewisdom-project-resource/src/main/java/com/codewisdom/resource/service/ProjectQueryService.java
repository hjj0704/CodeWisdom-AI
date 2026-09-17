package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.dto.ProjectListItemView;
import com.codewisdom.resource.dto.ProjectMetaView;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectQueryService {

    private final ProjectMapper projectMapper;

    public ProjectQueryService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public List<ProjectListItemView> listForUser(long userId) {
        LambdaQueryWrapper<Project> query = new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, ProjectStatus.READY)
                .orderByDesc(Project::getUpdatedAt);
        if (userId > 0L) {
            query.eq(Project::getOwnerUserId, userId);
        }
        return projectMapper.selectList(query).stream().map(this::toListItem).toList();
    }

    public ProjectMetaView meta(long projectId, long userId) {
        return toView(requireReadyProject(projectId, userId));
    }

    public Project requireExportable(long projectId, long userId) {
        Project project = requireReadyProject(projectId, userId);
        if (!Boolean.TRUE.equals(project.getExportEnabled())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "该项目未开启 ZIP 导出");
        }
        return project;
    }

    public Project requireReadyProject(long projectId, long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getStatus() != ProjectStatus.READY) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目不存在或未就绪: " + projectId);
        }
        assertAccess(project, userId);
        return project;
    }

    private void assertAccess(Project project, long userId) {
        Long owner = project.getOwnerUserId();
        if (owner == null || owner == 0L) {
            return;
        }
        if (userId <= 0L || !owner.equals(userId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
    }

    private ProjectListItemView toListItem(Project project) {
        return new ProjectListItemView(
                project.getId(),
                project.getName(),
                project.getSourceType(),
                project.getSourceUrl(),
                project.getFileCount() == null ? 0 : project.getFileCount(),
                project.getTotalSize() == null ? 0L : project.getTotalSize(),
                project.getUpdatedAt());
    }

    private static ProjectMetaView toView(Project project) {
        return new ProjectMetaView(
                project.getId(),
                project.getName(),
                project.getSourceType(),
                project.getSourceUrl(),
                project.getDefaultBranch(),
                Boolean.TRUE.equals(project.getExportEnabled()));
    }
}
