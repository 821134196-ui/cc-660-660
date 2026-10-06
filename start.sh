#!/usr/bin/env bash
# 安全帽受冲击退役判定系统 —— 一键启动
# 用法：./start.sh        （构建前端 + 打包后端 + 启动，访问 http://localhost:8080）
set -euo pipefail
cd "$(dirname "$0")"

# ---- 定位 Java / Maven（优先系统命令，其次 ~/tools 下的便携版） ----
if ! command -v java >/dev/null 2>&1; then
  for d in "$HOME"/tools/amazon-corretto-*/; do
    [ -x "${d}bin/java" ] && export JAVA_HOME="${d%/}" && export PATH="$JAVA_HOME/bin:$PATH" && break
  done
fi
if ! command -v mvn >/dev/null 2>&1; then
  for d in "$HOME"/tools/apache-maven-*/; do
    [ -x "${d}bin/mvn" ] && export PATH="${d}bin:$PATH" && break
  done
fi
command -v java >/dev/null 2>&1 || { echo "未找到 Java 17+，请先安装 JDK"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "未找到 Maven，请先安装"; exit 1; }
command -v npm  >/dev/null 2>&1 || { echo "未找到 Node.js/npm，请先安装"; exit 1; }

echo "==> [1/4] 构建前端"
cd frontend
[ -d node_modules ] || npm install --no-audit --no-fund
npm run build
cd ..

echo "==> [2/4] 部署前端到后端静态资源"
rm -rf backend/src/main/resources/META-INF/resources
mkdir -p backend/src/main/resources/META-INF/resources
cp -r frontend/dist/. backend/src/main/resources/META-INF/resources/

echo "==> [3/4] 打包后端（跳过测试；跑测试请用 mvn test）"
mkdir -p data/attachments
(cd backend && mvn -q -B package -DskipTests)

echo "==> [4/4] 启动服务  http://localhost:8080"
exec java -jar backend/target/quarkus-app/quarkus-run.jar
