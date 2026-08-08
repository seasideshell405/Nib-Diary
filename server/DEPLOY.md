# 服务器部署指南

日记服务器是一个 Go 单二进制服务，数据存 SQLite 数据库 + 图片文件目录。

## 构建

```bash
cd server
go build -o diary-server ./cmd/server
```

产物是单个可执行文件 `diary-server`。

## 配置（环境变量）

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `DIARY_TOKEN` | 是 | — | 与客户端共享的 API Token（随机长字符串） |
| `DIARY_ADDR` | 否 | `:8080` | 监听地址 |
| `DIARY_DB` | 否 | `data/diary.db` | SQLite 数据库文件路径 |
| `DIARY_IMAGES` | 否 | `data/images` | 图片文件目录 |

生成 Token：

```bash
openssl rand -hex 32
```

## systemd 服务

创建 `/etc/systemd/system/diary.service`：

```ini
[Unit]
Description=Diary Sync Server
After=network.target

[Service]
User=diary
Group=diary
WorkingDirectory=/opt/diary
Environment=DIARY_TOKEN=你的Token
Environment=DIARY_ADDR=:8080
Environment=DIARY_DB=/opt/diary/data/diary.db
Environment=DIARY_IMAGES=/opt/diary/data/images
ExecStart=/opt/diary/diary-server
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo useradd -r -s /usr/sbin/nologin diary
sudo mkdir -p /opt/diary/data
sudo cp diary-server /opt/diary/
sudo chown -R diary:diary /opt/diary
sudo systemctl daemon-reload
sudo systemctl enable --now diary
sudo systemctl status diary
```

## HTTPS

推荐用 Caddy 自动配证书（反代到本地端口）：

`/etc/caddy/Caddyfile`：

```
diary.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

```bash
sudo systemctl reload caddy
```

客户端填写的服务器地址为 `https://diary.example.com`。

## 验证

```bash
curl -H "Authorization: Bearer 你的Token" https://diary.example.com/healthz
# 期望 {"status":"ok"}
```

## 数据备份

按 ADR 约定双端互为备份，服务器侧不做脚本化备份。如需额外保险，可定期备份
`data/diary.db` 与 `data/images/`（服务器停止时复制 DB 文件最稳妥）。

## 升级

```bash
sudo systemctl stop diary
sudo cp diary-server /opt/diary/   # 新二进制
sudo systemctl start diary
```

SQLite 迁移在启动时自动执行，无需手工操作。
