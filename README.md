# 安全帽受冲击退役判定系统

工厂安全帽全生命周期管理：登记 → 领用 → 撞击冻结 → 安全员检查（复用/复检/退役）→ 退役台账。

## 一键启动

```bash
./start.sh
```

启动后访问 http://localhost:8080 （前端页面与后端 API 同端口）。
首次启动自动建库（`data/helmet.db`，SQLite）并写入演示数据（HMT-1001 ~ HMT-1007，覆盖在库/已领出/冻结待检/复检中/已退役/已超期各状态）。

要求：JDK 17+、Maven、Node.js（脚本会优先使用系统命令，其次 `~/tools` 下的便携版）。

## 运行测试

```bash
cd backend && mvn test
```

覆盖核心规则：撞击即冻结、复检退回保持冻结、退役不可逆、旧编号拦截、程度不明/超期/损伤不得复用等。

## 业务规则（服务端强制）

| 规则 | 实现 |
| --- | --- |
| 撞击即冻结 | 上报撞击后帽子立即 `FROZEN`，领用接口返回 409 |
| 冻结/退役禁止领用 | 仅 `IN_STOCK` 可发放；扫码页显示拦截原因 |
| 程度不明不默认合格 | `severity=UNKNOWN` 时复用被 400 拒绝，只能复检或退役 |
| 复检退回保持冻结 | 结论为复检 → 事件转 `IN_RECHECK`，帽子仍 `FROZEN`，可再次提交检查 |
| 超期/损伤不得复用 | 超过使用年限或外壳/内衬损伤 → 复用被 400 拒绝 |
| 复用须注明下次检查日 | 缺日期 400 |
| 退役不可逆 | 无修改/删除退役记录接口；无任意改状态接口；`retirement_records.helmet_id` 唯一 |
| 旧编号拦截 | `helmets.code` 唯一，退役编号重复登记 409；扫码显示退役并禁止领用 |
| 事故可追溯 | 撞击事件快照当时使用人 `userAtImpact` |

## 技术栈与结构

- 后端：Java 17 + Quarkus 3（REST/Hibernate ORM）+ SQLite（`data/helmet.db`）
- 前端：Svelte + Vite，构建产物由 Quarkus 静态托管
- 附件：检查附件存本地 `data/attachments/<检查ID>/`

```
backend/   Quarkus 服务（API 见下）
frontend/  Svelte 页面：扫码收发 / 台账 / 检查工作台 / 退役记录
data/      运行期生成：SQLite 库、附件（已 gitignore）
start.sh   一键构建并启动
```

## API 摘要

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/scan/{code}` | 扫码：状态 + 可否领用 + 拦截原因 |
| GET/POST | `/api/helmets` | 台账列表 / 登记（编号唯一） |
| GET | `/api/helmets/{id}` | 档案：领用历史 + 事件 + 检查 + 附件 |
| POST | `/api/helmets/{id}/issue` `/return` | 领用 / 归还 |
| POST | `/api/helmets/{id}/impacts` | 上报撞击（立即冻结） |
| GET | `/api/impacts?status=` | 检查队列（PENDING / IN_RECHECK） |
| POST | `/api/impacts/{id}/inspections` | 提交检查（multipart，含附件） |
| GET | `/api/retirements` | 退役台账（只读） |
| GET | `/api/attachments/{id}/download` | 附件下载 |
