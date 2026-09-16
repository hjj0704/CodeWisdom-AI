#!/usr/bin/env bash
# 在 ECS 上执行：bash /opt/codewisdom/deploy/server-verify.sh
set -euo pipefail

cd /opt/codewisdom/deploy
set -a && . ./.env && set +a

echo "=== Docker 中间件 ==="
docker compose -f docker-compose.prod.yml ps

echo "=== systemd 服务 ==="
systemctl list-units 'codewisdom@*' --no-pager

echo "=== 网关路由 ping ==="
for svc in project-resource code-analysis agent-orchestration evaluation-export; do
  curl -sf "http://127.0.0.1:8080/api/${svc}/ping" | head -c 120
  echo "  ($svc)"
done

echo "=== Flyway 表 ==="
docker exec cw-mysql mysql -u"$CW_DB_USER" -p"$CW_DB_PASSWORD" -e \
  "SHOW TABLES LIKE 't_%'; SHOW TABLES LIKE 'flyway%';" "$CW_MYSQL_DATABASE"

echo "=== DeepSeek 环境变量（只显示是否已配置，不打印密钥）==="
if [ -n "${CW_DEEPSEEK_API_KEY:-}" ]; then echo "CW_DEEPSEEK_API_KEY=已设置"; else echo "CW_DEEPSEEK_API_KEY=未设置"; fi
echo "CW_LLM_PROVIDER=${CW_LLM_PROVIDER:-未设置}"
