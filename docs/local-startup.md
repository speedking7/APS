# APS 本地启动

当前仓库的稳定本地启动路径是：

- Windows PowerShell 负责触发
- WSL 负责 `MySQL + Maven + Java`
- 应用优先尝试 `8080`，如果 Windows 已占用则自动回退到 `8081`

这条路径不依赖 Windows 侧 `mvn` 或 `docker`，适合当前机器直接复用。

## 一键命令

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-aps-local.ps1
```

查看状态：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\status-aps-local.ps1
```

停止服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-aps-local.ps1
```

## 脚本行为

`start-aps-local.ps1` 会调用 `scripts/aps-local.sh`，自动完成这些步骤：

1. 检查 WSL 内是否存在 `java`、`mvn`、`mysql`、`mysqld`
2. 初始化或启动用户态 MySQL：
   - 数据目录：`~/aps-mysql/data`
   - 端口：`3307`
   - 数据库：`aps_db`
3. 停掉上一次 APS 进程
4. 执行 `mvn -q clean package -DskipTests`
5. 启动 `aps-system/target/aps-system-1.0.0.jar`
6. 轮询 `GET /api/forecast`，确认服务真实可访问后才返回
7. 应用启动后不会自动初始化业务测试数据，按需手工导入即可

运行时文件：

- 应用日志：`aps-system/.local-run/app.log`
- 应用错误日志：`aps-system/.local-run/app.err.log`
- 应用 PID：`aps-system/.local-run/app.pid`
- 应用端口：`aps-system/.local-run/app.port`

## 测试数据初始化

本地启动主路径不再自动初始化测试主数据。

当前行为是：

- 启动 jar 时显式使用 `spring.sql.init.mode=never`
- 服务启动后不会自动灌 BOM、需求、安全库存、盘点数、设备清单、零件主数据、共模规则、稼动天数
- 业务测试数据由你按需手工导入

当前仓库根目录提供了一套基于 `template-bom.xlsx` 模拟出的可导入模板文件：

- `template-demand.xlsx`
- `template-inventory.xlsx`
- `template-safety-stock.xlsx`
- `template-operating-days.xlsx`
- `template-equipment-catalog.xlsx`
- `template-part-master.xlsx`
- `template-shared-mold-rules.xlsx`

这些文件的月份窗口固定为：

- `202606`
- `202607`
- `202608`

说明：

- `t_demand` 是当前计划计算测试主路径的一部分，使用 `template-demand.xlsx` 导入
- `t_forecast` 仍是遗留链路，如页面需要可单独维护
- 如果以后你更新了 `template-bom.xlsx`，可以重新生成这些模板文件再手工导入

## 端口规则

PowerShell 包装脚本会先看 Windows 侧端口占用：

- `8080` 空闲：默认启动到 `8080`
- `8080` 已占用：自动改用 `8081`

手工指定端口：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-aps-local.ps1 -Port 8081
```

## 访问方式

启动后用状态脚本确认地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\status-aps-local.ps1
```

输出里会包含：

- `APP_PORT`
- `APP_URL`
- `APP_HEALTH`

当前机器上，Windows 侧直接访问 WSL 服务要使用 `APP_URL` 里的 WSL IP，例如：

```text
http://172.24.x.x:8081/api/forecast
```

## 8080 问题

当前这台机器的 `8080` 被 Windows `iphlpsvc` 持有，不是 APS 占用。检查结果：

- `Get-NetTCPConnection -LocalPort 8080` 显示 PID `4280`
- `tasklist /svc /fi "PID eq 4280"` 显示服务 `iphlpsvc`
- `netsh interface portproxy show all` 显示已有 `8080 -> 172.20.117.171:8080` 端口转发

这意味着：

- 当前会话无法直接释放 `8080`
- `netsh interface portproxy delete ...` 需要管理员权限
- 所以脚本默认采用“自动回退到 `8081`”策略

如果你要恢复 Windows 本机 `8080`，请用管理员 PowerShell 执行：

```powershell
netsh interface portproxy show all
netsh interface portproxy delete v4tov4 listenport=8080 listenaddress=0.0.0.0
netsh interface portproxy delete v4tov4 listenport=8080 listenaddress=172.16.25.160
Restart-Service iphlpsvc
```

执行后再重新运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-aps-local.ps1 -Port 8080
```

注意：即使释放了 `8080`，Windows `localhost:8080` 是否能自动转发到 WSL，仍取决于你的 WSL/Windows 网络转发配置。如果没有自动转发，继续使用状态脚本给出的 `APP_URL` 最稳。

## Windows Maven / Docker 现状

当前机器状态：

- Windows `java` 已安装并可用
- Windows `mvn` 不在 `PATH`
- Windows `docker` 不在 `PATH`
- WSL 内 `mvn` 可用
- WSL 内 `docker` 客户端和 `docker-compose` 可用，但 `dockerd` 没有运行

所以现在的结论是：

- 本地开发主路径：用仓库脚本走 WSL
- 如果你要补齐 Windows 原生环境，至少还需要：
  - Windows Maven 安装并入 `PATH`
  - Docker Desktop 或等价 daemon

## 常用命令

跳过构建，直接用现有 jar 重启：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-aps-local.ps1 -SkipBuild
```

只停应用，保留 MySQL：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-aps-local.ps1 -KeepMySql
```

## 故障排查

如果启动失败，优先看：

```powershell
Get-Content .\aps-system\.local-run\app.log -Tail 80
Get-Content .\aps-system\.local-run\app.err.log -Tail 80
```

再看 WSL 里的 MySQL 日志：

```powershell
wsl bash -lc "tail -80 ~/aps-mysql/log/mysql.log"
```
