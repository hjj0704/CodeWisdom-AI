package com.codewisdom.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.domain.enums.FileNodeType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件树节点（自引用树）。
 *
 * <p>同时保存父子关系（{@code parentId}）与完整相对路径（{@code path}）：
 * 前者用于树形渲染，后者用于按路径直接定位、以及解析阶段的批量筛选。
 * 冗余是刻意的——用空间换掉递归查父链的开销。
 */
@Data
@TableName("t_file_node")
public class FileNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 父节点 id；为 null 表示项目根节点。 */
    private Long parentId;

    /** 相对项目根的路径，使用 {@code /} 分隔，例如 {@code src/main/java/demo/App.java}。 */
    private String path;

    /** 节点名（目录名或文件名）。 */
    private String name;

    private FileNodeType nodeType;

    /** 语言标识（java / python / ...），非源码文件为 null。 */
    private String language;

    private FileCategory category;

    /** 文件字节数；目录为 0。 */
    private Long size;

    /** 内容校验和，用于增量解析时判断文件是否变化。 */
    private String checksum;

    /** 创建时间。只在插入时填充，更新时永不写回——审计字段必须不可变。 */
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
