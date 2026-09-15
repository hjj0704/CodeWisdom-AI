package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.domain.enums.FileNodeType;
import com.codewisdom.resource.dto.FileTreeNode;
import com.codewisdom.resource.dto.FileTreeStats;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 文件树查询与统计。
 *
 * <p><b>一次查询、内存组装</b>：不做「逐层递归查子节点」——那是典型的 N+1，
 * 一个中等项目就能打出上千次查询。改为一次性按 {@code project_id} 取出全部节点，
 * 再用 {@code parentId → children} 的映射在内存里拼树，复杂度 O(n)。
 *
 * <p>之所以可以全量取：导入阶段对单项目节点数有硬上限（{@code codewisdom.import.max-files}，
 * 默认 20000），全量加载是有界的。也正因为这个上限，没有为 {@code path} 建索引——
 * MySQL 8 下 {@code VARCHAR(1024)} 全列索引会超出键长上限，要建就得用前缀索引，
 * 而 H2 不支持该语法，会破坏「同一份建表脚本两边都能跑」的约定。
 */
@Service
public class FileTreeService {

    /** 排序规则：目录在前，其次按名称升序，保证同一份数据的输出稳定可比对。 */
    private static final Comparator<FileNode> NODE_ORDER = Comparator
            .comparing((FileNode n) -> n.getNodeType() != FileNodeType.DIR)
            .thenComparing(FileNode::getName, Comparator.nullsLast(Comparator.naturalOrder()));

    private final FileNodeMapper fileNodeMapper;
    private final ProjectMapper projectMapper;

    public FileTreeService(FileNodeMapper fileNodeMapper, ProjectMapper projectMapper) {
        this.fileNodeMapper = fileNodeMapper;
        this.projectMapper = projectMapper;
    }

    /**
     * 构建项目文件树。
     *
     * <p>返回一个<b>虚拟根</b>（{@code id} 为 null，{@code path} 为空串）代表项目本身，
     * 其子节点是工作区的一级条目。用虚拟根而非返回多个根，是为了给前端一个稳定形状——
     * 无论项目有几个顶层目录，树的结构都一致。
     *
     * @param projectId 项目 id
     * @param maxDepth  最大展开深度；null 表示不限。1 表示只返回一级条目
     */
    public FileTreeNode buildTree(Long projectId, Integer maxDepth) {
        Project project = requireProject(projectId);
        List<FileNode> nodes = loadNodes(projectId);

        Map<Long, List<FileNode>> childrenByParent = groupByParent(nodes);
        List<FileTreeNode> children = assemble(childrenByParent, maxDepth, 1);

        return new FileTreeNode(null, null, project.getName(), "", FileNodeType.DIR,
                null, FileCategory.OTHER, 0L, children);
    }

    /**
     * 取指定路径的子树。路径不存在时抛 {@link ErrorCode#NOT_FOUND}。
     *
     * @param path 相对项目根的路径，例如 {@code src/main/java}
     */
    public FileTreeNode findSubtree(Long projectId, String path) {
        requireProject(projectId);
        String normalized = normalizePath(path);
        List<FileNode> nodes = loadNodes(projectId);

        FileNode target = nodes.stream()
                .filter(n -> n.getPath().equals(normalized))
                .findFirst()
                .orElseThrow(() -> BizException.of(ErrorCode.NOT_FOUND, "路径不存在: " + normalized));

        Map<Long, List<FileNode>> childrenByParent = groupByParent(nodes);
        return toNode(target, childrenByParent, null, 1);
    }

    /**
     * 分类统计。
     *
     * <p>统计口径与 {@code t_project} 上的冗余计数字段一致：
     * {@code fileCount} 只算文件，目录不计入、体积也不计。
     */
    public FileTreeStats stats(Long projectId) {
        requireProject(projectId);
        List<FileNode> nodes = loadNodes(projectId);

        int directories = 0;
        int files = 0;
        long totalSize = 0;
        int maxDepth = 0;
        Map<FileCategory, Integer> byCategory = new EnumMap<>(FileCategory.class);
        Map<String, Integer> byLanguage = new TreeMap<>();

        for (FileNode node : nodes) {
            if (node.getNodeType() == FileNodeType.DIR) {
                directories++;
                maxDepth = Math.max(maxDepth, depthOf(node.getPath()));
                continue;
            }
            files++;
            totalSize += node.getSize() == null ? 0 : node.getSize();
            byCategory.merge(node.getCategory(), 1, Integer::sum);
            if (node.getLanguage() != null && !node.getLanguage().isBlank()) {
                byLanguage.merge(node.getLanguage(), 1, Integer::sum);
            }
        }

        return new FileTreeStats(projectId, directories + files, directories, files,
                totalSize, maxDepth, byCategory, byLanguage);
    }

    // ---- 内部 ----

    private List<FileNode> loadNodes(Long projectId) {
        return fileNodeMapper.selectList(new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getProjectId, projectId));
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目不存在: " + projectId);
        }
        return project;
    }

    /** 按 parentId 分组；根节点的 key 为 null。 */
    private static Map<Long, List<FileNode>> groupByParent(List<FileNode> nodes) {
        Map<Long, List<FileNode>> grouped = new HashMap<>();
        for (FileNode node : nodes) {
            grouped.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
        }
        grouped.values().forEach(list -> list.sort(NODE_ORDER));
        return grouped;
    }

    private static List<FileTreeNode> assemble(Map<Long, List<FileNode>> childrenByParent,
                                               Integer maxDepth,
                                               int currentDepth) {
        List<FileNode> children = childrenByParent.get(null);
        if (children == null) {
            return List.of();
        }
        List<FileTreeNode> result = new ArrayList<>(children.size());
        for (FileNode child : children) {
            result.add(toNode(child, childrenByParent, maxDepth, currentDepth));
        }
        return result;
    }

    private static FileTreeNode toNode(FileNode node,
                                       Map<Long, List<FileNode>> childrenByParent,
                                       Integer maxDepth,
                                       int currentDepth) {
        List<FileTreeNode> children = List.of();
        boolean isDir = node.getNodeType() == FileNodeType.DIR;
        boolean depthAllowed = maxDepth == null || currentDepth < maxDepth;

        if (isDir && depthAllowed) {
            List<FileNode> raw = childrenByParent.getOrDefault(node.getId(), List.of());
            children = new ArrayList<>(raw.size());
            for (FileNode child : raw) {
                children.add(toNode(child, childrenByParent, maxDepth, currentDepth + 1));
            }
        }

        return new FileTreeNode(node.getId(), node.getParentId(), node.getName(), node.getPath(),
                node.getNodeType(), node.getLanguage(), node.getCategory(),
                node.getSize() == null ? 0L : node.getSize(), children);
    }

    /** {@code src/main/java} → 深度 3；顶层条目 → 1。 */
    private static int depthOf(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        int depth = 1;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
