# CodeWisdom —— 一键更新阿里云 ECS（47.93.158.48）
#
# 用法（在仓库根目录 PowerShell）：
#   .\deploy\update-ecs.ps1                    # 更新全部 5 个服务
#   .\deploy\update-ecs.ps1 -Service agent-orchestration   # 只更新一个
#
# 前置：本机已配置 SSH 公钥到 root@47.93.158.48（ssh root@47.93.158.48 能免密登录）

param(
    [string]$Host = "47.93.158.48",
    [string]$User = "root",
    [string]$Service = "all"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

$Services = @(
    "gateway",
    "project-resource",
    "code-analysis",
    "agent-orchestration",
    "evaluation-export"
)

Write-Host ">> mvn package ..."
mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvn package 失败" }

if ($Service -ne "all") {
    if ($Services -notcontains $Service) { throw "未知服务: $Service" }
    $Services = @($Service)
}

foreach ($svc in $Services) {
    $jar = "codewisdom-$svc\target\codewisdom-$svc-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "找不到 $jar" }

    Write-Host ">> 更新 codewisdom@$svc ..."
    ssh "${User}@${Host}" "sudo systemctl stop codewisdom@$svc"
    scp $jar "${User}@${Host}:/opt/codewisdom/jars/"
    ssh "${User}@${Host}" "sudo systemctl start codewisdom@$svc"
    Start-Sleep -Seconds 15
}

Write-Host ">> 验证网关路由 ..."
foreach ($svc in @("project-resource", "code-analysis", "agent-orchestration", "evaluation-export")) {
    $url = "http://${Host}:8080/api/$svc/ping"
    $resp = curl.exe -s -m 15 $url
    Write-Host "$svc -> $resp"
}

Write-Host "完成。日志：ssh ${User}@${Host} journalctl -u codewisdom@agent-orchestration -n 50"
