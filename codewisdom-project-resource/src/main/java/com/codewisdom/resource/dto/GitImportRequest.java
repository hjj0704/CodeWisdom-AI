package com.codewisdom.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Git 仓库导入请求。
 *
 * @param url    仓库地址（GitHub / Gitee 等公开仓库）
 * @param branch 指定分支；留空使用远端默认分支
 * @param name   项目显示名；留空则从仓库地址推导
 */
public record GitImportRequest(

        @NotBlank(message = "仓库地址不能为空")
        @Size(max = 512, message = "仓库地址过长")
        String url,

        @Size(max = 128, message = "分支名过长")
        @Pattern(regexp = "^[\\w./\\-]*$", message = "分支名包含非法字符")
        String branch,

        @Size(max = 128, message = "项目名过长")
        String name
) {
}
