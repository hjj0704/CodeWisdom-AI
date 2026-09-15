package com.codewisdom.resource.dto;

import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.domain.enums.FileNodeType;

import java.util.List;

/**
 * 文件树节点。
 *
 * @param id       节点 id；虚拟根节点为 null
 * @param parentId 父节点 id；根节点为 null
 * @param name     节点名
 * @param path     相对项目根的路径；虚拟根为空串
 * @param type     目录 / 文件
 * @param language 语言标识，仅源码文件有值
 * @param category 分类
 * @param size     文件字节数；目录为 0
 * @param children 子节点；文件为空列表
 */
public record FileTreeNode(
        Long id,
        Long parentId,
        String name,
        String path,
        FileNodeType type,
        String language,
        FileCategory category,
        Long size,
        List<FileTreeNode> children
) {
    /** 是否为虚拟根（项目本身，不对应数据库记录）。 */
    public boolean isVirtualRoot() {
        return id == null;
    }
}
