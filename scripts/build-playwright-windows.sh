#!/usr/bin/env bash
# ============================================================
#  XuanJi Build - Windows 专用包（含 Playwright 渲染能力）
#  Output:  xuanji-starter/target/xuanji-starter-1.0.0-SNAPSHOT.jar
#  Usage:   bash scripts/build-playwright-windows.sh  (JDK 25+ required)
#  产物:    仅含 Windows 平台 Playwright driver 的精简 jar
# ============================================================
set -e
cd "$(dirname "$0")/.."

echo "[XuanJi] 构建 Windows 专用包（含 Playwright）..."

# ---- 探测 JDK ----
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  : # JAVA_HOME 已设置
elif command -v java >/dev/null 2>&1; then
  : # java 在 PATH
else
  echo "[XuanJi] 错误: 未找到 JDK，请安装 JDK 25 或设置 JAVA_HOME"
  exit 1
fi

# ---- 探测 python ----
PY="python3"
command -v python3 >/dev/null 2>&1 || PY="python"
command -v "$PY" >/dev/null 2>&1 || { echo "[XuanJi] 错误: 未找到 python3/python"; exit 1; }
echo "[XuanJi] Python: $PY"

# ---- Maven 打包（全平台）----
echo "[XuanJi] 步骤 1/2: mvn clean package ..."
mvn -q clean package -DskipTests

# ---- 裁剪为 Windows 平台 ----
JAR="xuanji-starter/target/xuanji-starter-1.0.0-SNAPSHOT.jar"
echo "[XuanJi] 步骤 2/2: 裁剪 Playwright driver 为 Windows 平台 ..."
"$PY" scripts/slim-jar.py "$JAR" --platform win

echo ""
echo "[XuanJi] 构建完成!"
echo "[XuanJi] 产物: $JAR"
echo "[XuanJi] 启动: java -jar $JAR"
