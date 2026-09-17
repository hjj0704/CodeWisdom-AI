-- Git 导入时可选择是否允许 ZIP 导出；ZIP 导入默认允许。

ALTER TABLE t_project
    ADD COLUMN export_enabled TINYINT NOT NULL DEFAULT 1;
