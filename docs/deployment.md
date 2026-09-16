# 部署清单 —— 阿里云 ECS 上线 CodeWisdom AI

> **目标机器**：47.93.158.48（华北 2 · 北京）· 2 核 8G 经济型 e · Alibaba Cloud Linux 3 · 40G ESSD
> **本文用途**：照着从上往下做，每步都有**验证方法**。卡住时先看最后一节「排错」。
>
> ⚠️ **两条铁律，先看**：
> 1. **不在服务器上构建**。本机构建好只传 jar。服务器是共享型 CPU，编译会打满它，
>    还得多占 4.6G 的 Maven 仓库。服务器上只装 **Docker + JDK 17**。
> 2. **中间件端口一律不绑 0.0.0.0**。`docker-compose.prod.yml` 里全是 `127.0.0.1:xxxx`，
>    配合安全组形成两道防线。要管理控制台走 **SSH 隧道**，不开公网端口。

---

## 第 0 步 · 本机构建产物（在 Windows 这台机器上做）

```bash
cd D:/CodeWisdom
mvn clean verify -DskipTests=false     # 确认 421 个测试全绿，再打包
mvn package -DskipTests
```

产物：`<module>/target/codewisdom-<name>-1.0.0-SNAPSHOT.jar`，共 5 个（网关 + 4 个服务）。

**要先跑的验证**：`mvn clean verify` 必须 **421 个测试、0 失败**。基线不对就先别往上传。

---

## 第 1 步 · 服务器初始化

```bash
# 用 root 或 sudo 都行
sudo mkdir -p /opt/codewisdom/{jars,deploy} /var/log/codewisdom
sudo adduser --system --no-create-home codewisdom 2>/dev/null || true
```

**验证**：`ls -d /opt/codewisdom/jars /var/log/codewisdom` 两个目录都在。

---

## 第 2 步 · 装 Docker 与 JDK 17

```bash
# Alibaba Cloud Linux 3 自带 docker 包，且**软件源在阿里云内网**——不耗你的公网流量
sudo dnf install -y docker java-17-openjdk
sudo systemctl enable --now docker
```

> 若 `dnf install docker` 提示找不到包，再走官方源（这条会走公网）：
> ```bash
> sudo dnf install -y dnf-plugins-core
> sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
> sudo dnf install -y docker-ce docker-compose-plugin
> sudo systemctl enable --now docker
> ```

**验证**：
```bash
docker --version            # 期望 Docker version 2x/29.x
docker compose version      # 期望 v2.x 或 v5.x
java -version               # 必须是 17.x —— 不是 8、不是 11
```

> ⚠️ **`java -version` 必须是 17**。父 pom 里写死 `<java.version>17</java.version>`，
> 版本不对时服务会以「UnsupportedClassVersionError」启动失败，而且报错信息不直观。

---

## 第 3 步 · 配 Docker 镜像加速器（**必做，否则拉不动镜像**）

**国内机器直连 Docker Hub 是不通的**（本机已实测：`registry-1.docker.io` 超时）。
不配这一步，第 5 步会卡在拉镜像。

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ]
}
EOF
sudo systemctl restart docker
```

**验证**：
```bash
docker info --format '{{json .RegistryConfig.Mirrors}}'
# 期望输出包含上面两个地址
docker pull hello-world && echo "拉取链路通了 ✓"
```

> 公共镜像站常关停。若某天拉不动了，换一个能用的地址即可，配置只在 `/etc/docker/daemon.json` 一处。

---

## 第 4 步 · 传文件上台

在本机 Windows 上（把 `<部署目录>` 换成你实际传文件用的路径）：

```bash
# 部署文件
scp deploy/docker-compose.prod.yml deploy/.env.example deploy/codewisdom@.service \
    root@47.93.158.48:/opt/codewisdom/deploy/

# 5 个 jar（约 216MB；上传是入流量，通常不计费）
scp codewisdom-gateway/target/*.jar \
    codewisdom-project-resource/target/*.jar \
    codewisdom-code-analysis/target/*.jar \
    codewisdom-agent-orchestration/target/*.jar \
    codewisdom-evaluation-export/target/*.jar \
    root@47.93.158.48:/opt/codewisdom/jars/
```

> 各模块的 `target/*.original` 是 Spring Boot repackage 前的原始 jar，**不要传**。
> 用 `*SNAPSHOT.jar` 精确匹配，或传完在服务器上 `rm -f /opt/codewisdom/jars/*.original`。

**验证**：服务器上 `ls /opt/codewisdom/jars/` 应有 **5 个** jar，没有 `.original`。

---

## 第 5 步 · 配置 .env 并起中间件

```bash
cd /opt/codewisdom/deploy
cp .env.example .env

# 生成 Nacos 的鉴权令牌（必须是 Base64，且解码后 >= 32 字符）
echo -n "codewisdom-$(openssl rand -hex 24)" | base64 -w0; echo

vi .env      # 把模板里所有「改成你自己的」都改掉，Nacos 那行粘上刚生成的
chmod 600 .env
```

> ⚠️ **Nacos 3.x 强制要求 `NACOS_AUTH_TOKEN`**，不配会 `exit 255` 崩溃重启。
> 只给 `MODE: standalone` 不够——这是 3.x 相对 2.x 的行为变化。

```bash
docker compose -f docker-compose.prod.yml up -d
```

首次会拉约 **2.5G** 镜像，视网络几分钟到十几分钟。

**验证 —— 5 个容器全部 healthy**（这一条不过就别往下走）：
```bash
docker compose -f docker-compose.prod.yml ps
# 期望每个都是 Up (healthy)。nacos 要等 1~2 分钟
```

**再验一层：不只看 healthy 标志，逐个真实调用**：
```bash
cd /opt/codewisdom/deploy && set -a && . ./.env && set +a

# MySQL：业务账号能连、库存在
docker exec cw-mysql mysql -u"$CW_DB_USER" -p"$CW_DB_PASSWORD" -e "SELECT VERSION()" "$CW_MYSQL_DATABASE"

# Redis
docker exec cw-redis redis-cli ping                       # 期望 PONG

# Nacos：命名服务（应用做服务发现用的就是这个端点）
curl -s -o /dev/null -w "nacos metrics HTTP %{http_code}\n" http://127.0.0.1:8848/nacos/v1/ns/operator/metrics

# MinIO / RabbitMQ
curl -s -o /dev/null -w "minio HTTP %{http_code}\n" http://127.0.0.1:9000/minio/health/live
docker exec cw-rabbitmq rabbitmq-diagnostics -q ping       # 期望 Ping succeeded
```

---

## 第 6 步 · 装 systemd 单元并起服务

```bash
sudo cp /opt/codewisdom/deploy/codewisdom@.service /etc/systemd/system/
sudo systemctl daemon-reload
```

**⚠️ 分批启动，不要一次起 6 个**：2 核机器上 6 个 Spring Boot 同时抢 CPU，
启动时间会翻几倍，还可能因为健康检查超时而反复重启。

```bash
# 第一批：网关（它不依赖数据库）
sudo systemctl enable --now codewisdom@gateway
sleep 30

# 第二批：一次一个，每个等就绪再下一个
for svc in project-resource code-analysis agent-orchestration evaluation-export; do
  sudo systemctl enable --now codewisdom@$svc
  echo "已启动 $svc，等待就绪…"
  sleep 30
done
```

**验证**：
```bash
systemctl list-units 'codewisdom@*' --no-pager     # 5 个都应是 active (running)
curl -s http://127.0.0.1:8080/ping                  # 网关
curl -s http://127.0.0.1:8081/ping                  # project-resource
```

**看日志**：
```bash
journalctl -u codewisdom@code-analysis -n 100 --no-pager
# 或
tail -f /var/log/codewisdom/code-analysis.log
```

**关键验证 —— code-analysis 启动时 Flyway 自动建表**（T-105 的验收点）：
```bash
tail -50 /var/log/codewisdom/code-analysis.log | grep -iE "flyway|migrat"
# 期望看到：Successfully applied N migration ... now at version v1

docker exec cw-mysql mysql -u"$CW_DB_USER" -p"$CW_DB_PASSWORD" -e "SHOW TABLES" "$CW_MYSQL_DATABASE"
# 期望看到 t_audit_issue 与 flyway_schema_history_code_analysis
```

---

## 第 7 步 · 安全组与对外访问

**入方向只开三条**（阿里云控制台 → 安全组 → 入方向）：

| 端口 | 授权对象 | 用途 |
|---|---|---|
| 22 | **你自己的公网 IP/32** | SSH。**不要 0.0.0.0/0** |
| 8080 | 0.0.0.0/0 | 网关，对外唯一入口 |
| 80 / 443 | 0.0.0.0/0 | 前端（阶段 10 之后再开） |

**其余端口一条都不要开**：3306 / 6379 / 8848 / 9848 / 8849 / 9000 / 9001 / 5672 / 15672。
`docker-compose.prod.yml` 里它们已经只绑 127.0.0.1，安全组再挡一道。

**要看中间件控制台，用 SSH 隧道**（在你自己电脑上执行，**别关这个窗口**）：
```bash
ssh -L 8849:127.0.0.1:8849 -L 9001:127.0.0.1:9001 -L 15672:127.0.0.1:15672 root@47.93.158.48
```
然后本地浏览器访问：
- Nacos 控制台 → `http://localhost:8849/`
  ⚠️ **路径是根 `/`，不是 `/nacos/`**。Nacos 3.x 把控制台拆成了独立应用，
  访问 `/nacos/` 会返回 `500 No static resource nacos.`——那不是装坏了。
- MinIO 控制台 → `http://localhost:9001/`
- RabbitMQ 管理台 → `http://localhost:15672/`

**外网验证网关**：
```bash
curl -s http://47.93.158.48:8080/ping
# 期望：{"code":0,"data":"codewisdom-gateway",...}
```

---

## 第 8 步 · 省额度（重要）

300 元额度按 `0.367 元/小时` 算 = **817 小时**，而连续跑满 3 个月需要 2160 小时（793 元，会超支）。

**推荐节奏：每天开 9 小时 × 90 天 = 810 小时 ≈ 297 元，正好用完额度。**

关机与开机：
```bash
# 关机（在阿里云控制台操作，或）
sudo shutdown -h now

# 开机后确认中间件自动起来了（restart: unless-stopped 会拉起来）
docker compose -f /opt/codewisdom/deploy/docker-compose.prod.yml ps
```

> ⚠️ **关机只停「计算」费（vCPU/内存），云盘继续计费**，公网 IP 也可能继续算。
> 具体以控制台账单为准——**建议开机跑一天后看一眼「费用账单 → 明细」**，
> 别等两周后才发现额度掉得比预期快。

**其他省法**：
- 不用时只关服务不关机器 → **省不了钱**，计算费照收。要省就关机。
- 反复 `docker compose down -v && up -d` 会重拉 2.5G 镜像 → **别这么干**，数据卷也会丢。

---

## 第 9 步 · 后续更新（改了代码之后）

```bash
# 本机
mvn package -DskipTests
scp codewisdom-code-analysis/target/codewisdom-code-analysis-1.0.0-SNAPSHOT.jar \
    root@47.93.158.48:/opt/codewisdom/jars/

# 服务器
sudo systemctl restart codewisdom@code-analysis
```

⚠️ 覆盖 jar 前**先停服务**，否则「文件被占用」删除失败（本机踩过一次：
`maven-clean` 删不掉正在被 `java -jar` 持有的 jar）。
正确顺序：`systemctl stop` → 传 → `systemctl start`。

---

## 排错

| 现象 | 原因 | 处理 |
|---|---|---|
| `docker pull` 卡住 / 超时 | 没配镜像加速器，或加速器挂了 | 回到第 3 步；换一个可用地址 |
| nacos 容器不断重启，日志 `NACOS_AUTH_TOKEN must be set` | Nacos 3.x 强制鉴权 | 第 5 步生成令牌填进 `.env` |
| nacos 容器起来了但一直 `unhealthy` | 探针地址不对（3.x 移除了 v1 console health，返回 410） | 已改用 `/nacos/v1/ns/operator/metrics`，确认用的是 `docker-compose.prod.yml` |
| 访问 `:8849/nacos/` 返回 500 | Nacos 3.x 控制台在**根路径** | 用 `http://localhost:8849/` |
| 服务启动报 `Access denied for user` | `.env` 里的 `CW_DB_USER/PASSWORD` 与容器建的用户不一致 | 两处都从同一个 `.env` 读；改了要 `docker compose down -v` 重建（**会清库**） |
| 服务启动报 `UnsupportedClassVersionError` | 服务器 JDK 不是 17 | `java -version` 确认，装 `java-17-openjdk` |
| 上下文启动失败、提示找不到数据源 | 打了错的 profile | 服务默认 profile 是 `local`，已指向 localhost:3306，确认中间件先起来了 |
| 机器卡死 / OOM | 没分批启动，或漏了 `mem_limit` | 分批启动；确认用的是 `docker-compose.prod.yml` |
| `mvn clean` 删不掉 jar | jar 正被 java 进程持有 | 先停服务再传 |
