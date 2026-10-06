#!/usr/bin/env bash
# =============================================================================
# 工厂安全帽受冲击退役判定系统 —— 一条命令本地启动
#
# 用法：
#   ./run.sh            首次：构建前端 + 打包后端 + 启动（之后直接复用构建产物）
#   ./run.sh --rebuild  强制重新构建前端与后端
#   ./run.sh --dev      开发模式（Quarkus 热重载；前端另见 frontend/README）
#
# 访问： http://localhost:8080
# 数据： backend/data/safetyhelmet.db（SQLite 本地文件）
# 附件： backend/data/uploads/
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

ROOT="$(pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
STATIC="$BACKEND/src/main/resources/META-INF/resources"
JAR="$BACKEND/target/quarkus-app/quarkus-run.jar"

REBUILD=0
DEV=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    --dev) DEV=1 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# ----------------------------- 1. 定位 Java 21 ------------------------------
pick_java_home() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME"; return
  fi
  for d in "$HOME"/tools/jdk-21* /opt/tools/jdk-21* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-21*; do
    if [ -x "$d/bin/java" ]; then echo "$d"; return; fi
  done
  echo ""
}

JH="$(pick_java_home)"
if [ -n "$JH" ]; then
  export JAVA_HOME="$JH"
  export PATH="$JH/bin:$PATH"
  echo "[run] 使用 JDK: $($JH/bin/java -version 2>&1 | head -1)"
elif command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -Eq '"(21|2[2-9]|[3-9][0-9])\.'; then
  echo "[run] 使用系统 java: $(java -version 2>&1 | head -1)"
else
  echo "[run] 未找到 Java 21+。请安装 Temurin JDK 21，或设置 JAVA_HOME。"
  echo "       下载地址：https://adoptium.net/temurin/releases/?version=21"
  exit 1
fi

# Maven：优先 wrapper，其次系统 mvn，再次 ~/tools
MVN=""
[ -x "$BACKEND/mvnw" ] && MVN="$BACKEND/mvnw"
if [ -z "$MVN" ] && command -v mvn >/dev/null 2>&1; then MVN="mvn"; fi
if [ -z "$MVN" ]; then
  for d in "$HOME"/tools/apache-maven-* ; do
    [ -x "$d/bin/mvn" ] && MVN="$d/bin/mvn" && break
  done
fi
[ -n "$MVN" ] || { echo "[run] 未找到 Maven（可用 mvn -N wrapper:wrapper 生成 wrapper）"; exit 1; }
echo "[run] Maven: $MVN"

# ----------------------------- 2. 构建前端（Svelte）--------------------------
if [ "$REBUILD" = 1 ] || [ ! -f "$STATIC/index.html" ]; then
  echo "[run] 构建 Svelte 前端 → Quarkus 静态资源目录 ..."
  if ! command -v npm >/dev/null 2>&1; then
    echo "[run] 需要 Node.js 18+ / npm 来构建前端（仅首次构建需要）"; exit 1
  fi
  ( cd "$FRONTEND" && npm install --no-audit --no-fund && npm run build )
else
  echo "[run] 前端产物已存在，跳过构建（--rebuild 可强制重建）"
fi

# ----------------------------- 3. 启动后端 ----------------------------------
# SQLite 相对路径 data/ 以工作目录为基准，统一在 backend/ 下运行（数据落在 backend/data/）
mkdir -p "$BACKEND/data"

if [ "$DEV" = 1 ]; then
  echo "[run] 开发模式启动： http://localhost:8080 （前端联调可在 frontend/ 执行 npm run dev，端口 5173）"
  exec "$MVN" -f "$BACKEND/pom.xml" quarkus:dev
fi

if [ "$REBUILD" = 1 ] || [ ! -f "$JAR" ]; then
  echo "[run] 打包 Quarkus 后端 ..."
  "$MVN" -f "$BACKEND/pom.xml" -DskipTests package
fi

cd "$BACKEND"
echo "[run] 启动系统： http://localhost:8080 （Ctrl+C 停止）"
exec java -jar "$JAR"
