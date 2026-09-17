# CodeWisdom —— 构建并部署前端到 ECS Nginx（T-1104）
#
# 用法（仓库根目录 PowerShell）：
#   .\deploy\deploy-frontend.ps1
#
# 前置：ECS 已安装 nginx，且 /opt/codewisdom/ui 可写

param(
    [string]$EcsHost = "47.93.158.48",
    [string]$SshUser = "root"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

Write-Host ">> npm run build ..."
Push-Location codewisdom-ui
npm run build
if ($LASTEXITCODE -ne 0) { throw "npm run build 失败" }
Pop-Location

$dist = Join-Path $Root "codewisdom-ui\dist"
if (-not (Test-Path $dist)) { throw "找不到 dist 目录" }

Write-Host ">> 上传静态资源 ..."
ssh "${SshUser}@${EcsHost}" "mkdir -p /opt/codewisdom/ui /opt/codewisdom/nginx"
scp -r "$dist\*" "${SshUser}@${EcsHost}:/opt/codewisdom/ui/"
scp "deploy\nginx\codewisdom.conf" "${SshUser}@${EcsHost}:/opt/codewisdom/nginx/codewisdom.conf"

Write-Host ">> 配置 Nginx ..."
ssh "${SshUser}@${EcsHost}" @"
if [ -d /etc/nginx/conf.d ]; then
  cp /opt/codewisdom/nginx/codewisdom.conf /etc/nginx/conf.d/codewisdom.conf
elif [ -d /etc/nginx/sites-available ]; then
  cp /opt/codewisdom/nginx/codewisdom.conf /etc/nginx/sites-available/codewisdom.conf
  ln -sf /etc/nginx/sites-available/codewisdom.conf /etc/nginx/sites-enabled/codewisdom.conf
fi
nginx -t && systemctl reload nginx
"@

Write-Host "完成。访问 http://${EcsHost}/"
