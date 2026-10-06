# 工厂安全帽受冲击退役判定系统

安全帽发生撞击后，仓库**立即暂停发放**，由安全员检查外壳、内衬及使用年限后判定
**复用 / 复检 / 退役**。系统用硬规则保证受损装备不会再次流到工人手中。

- 后端：Java 21 + Quarkus 3.15 + Hibernate ORM + SQLite（Quarkiverse sqlite 扩展）
- 前端：Svelte 4 + Vite（构建产物由 Quarkus 在同一端口提供）
- 存储：本地 SQLite 文件 `backend/data/safetyhelmet.db`；检查附件落本地磁盘
- 扫码：本地模拟（手输回车 / 演示码 / 一键“模拟扫码”）

## 一条命令启动

```bash
./run.sh
```

首次运行会自动 `npm install && npm run build`、Maven 打包，然后启动。
打开 <http://localhost:8080> 即可使用。

| 命令 | 说明 |
| --- | --- |
| `./run.sh` | 启动（已构建时直接起服务） |
| `./run.sh --rebuild` | 强制重建前端并重新打包 |
| `./run.sh --dev` | Quarkus 开发模式（热重载） |

前置条件：JDK 21、Maven 3.9+（脚本会自动识别 `~/tools` 下的安装）、Node.js 18+（仅首次构建前端需要）。

## 核心业务规则（均有后端强校验 + 自动化测试）

1. **受冲击立即冻结**：登记撞击事件后帽子变为 `FROZEN`，仓库扫码禁止领出。
2. **撞击程度不明不默认合格**：`severity` 缺省/非法一律按 `UNKNOWN` 处理，照样冻结。
3. **安全员三选一结论**：
   - `REUSE` 复用：外壳无裂纹、内衬无破损、未超使用年限，且**必须填写下次检查日**；
   - `REINSPECT` 退回复检：置 `REINSPECTING`，**复检期间仍保持冻结**，扫码/领用持续被拦，须再次检查直到复用或退役；
   - `RETIRE` 退役：终态，永久作废。
4. **退役不可撤销**：
   - 库存编辑接口只接受批次/生产日期，请求体夹带 `status` 直接 400；
   - 退役后再次提交检查结论翻案 → 409；
   - 退役旧编号**重新登记 → 409 拦截**，旧码再次扫码领用 → 409。
5. **使用年限闸口**：默认 3 年，由生产日期系统计算；即便状态在役，超年限帽扫码即拦截、不能领出。
6. **事故可追溯到人**：撞击事件必须填写“当时使用人”，档案页可按事故查到使用人及完整领用历史。
7. **归还不等于解封**：工人交回受冲击帽只完成归还，帽子仍冻结待检。

## 四个页面

| 页面 | 功能 |
| --- | --- |
| 仓库扫码 `#/scan` | 模拟扫码、大号状态卡、冻结/复检/退役醒目拦截、领出/归还登记 |
| 撞击上报 `#/impact` | 选程度（默认不明）、填使用人/地点/经过，提交即冻结 |
| 安全员检查 `#/inspect` | 外壳 + 内衬 + 系统核算年限 + 判定依据 + 三结论 + 附件上传、检查历史 |
| 库存档案 `#/inventory` | 登记（唯一编号+批次）、状态筛选、领用/事故/检查时间线、库存资料编辑 |

首次启动内置演示数据：`AQM-1001`（在役）、`AQM-1002`（被领用）、`AQM-1003`（冻结待检）、
`AQM-1004`（**复检退回**）、`AQM-1005`（**已退役旧码**）、`AQM-1006`（超年限拦截）。

## 重点验收路径

**复检退回（复检期间不能放出）**

```
AQM-1004 扫码 → REINSPECTING 禁领
  → 安全员检查：外壳/内衬合格 + 依据 + 复用 + 下次检查日
  → 恢复 IN_SERVICE，可领出
```

**旧码领用拦截（退役不可逆）**

```
AQM-1005 扫码 → RETIRED 禁领
  → PUT /api/helmets/AQM-1005 改批次成功但状态仍 RETIRED；夹带 status=IN_SERVICE 返回 400
  → POST /api/helmets 用同号重新登记 → 409 “旧编号永久作废”
```

## API 摘要

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/helmets?status=&code=` | 列表 / 扫码查询（含 canCheckout、blockReason） |
| POST | `/api/helmets` | 登记（退役旧码 409） |
| GET | `/api/helmets/{code}` | 完整档案：领用历史、撞击（含使用人）、检查 |
| PUT | `/api/helmets/{code}` | 仅改批次/生产日期（status 字段拒绝） |
| POST | `/api/helmets/{code}/impacts` | 撞击上报 → 立即冻结 |
| POST | `/api/helmets/{code}/checkout` | 领出（冻结/复检/退役/超期 → 409） |
| POST | `/api/helmets/{code}/return` | 归还 |
| POST | `/api/helmets/{code}/inspections` | 检查结论（复用/复检/退役 + 附件 base64） |
| GET | `/api/attachments/{id}/content` | 下载本地附件 |

## 测试

```bash
cd backend
mvn test
```

`HelmetLifecycleTest` 覆盖：程度不明冻结、**复检退回期间领用拦截→二次检查复用恢复**、
复用必填下次检查日、裂壳/超年限不得复用、**退役后领用/改库存/夹带字段/翻案/旧码重登记五路拦截**、
事故使用人追溯、超年限在役拦截、归还后仍冻结、附件落盘下载。

## 目录结构

```
backend/    Quarkus 工程（实体/服务/REST/测试，数据与附件在 backend/data/）
frontend/   Svelte 工程（vite build → backend/.../META-INF/resources）
run.sh      一条命令启动
```
