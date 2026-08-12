#!/usr/bin/env bash
# ============================================================
#  璇玑 XuanJi · 发布包构建脚本（跨平台：在哪个平台跑就产哪个平台的包）
#
#  产物:
#    Windows  → release/xuanji-<版本>-win.zip
#    Linux    → release/xuanji-<版本>-linux.tar.gz
#
#  发布包特性:
#    · 内置精简 JRE（jlink）——用户无需安装 JDK
#    · 内置 Playwright 平台驱动（图文卡片渲染可用）
#    · 自带启动脚本 + plugins/ + docs/，解压即用
#
#  前置环境（构建机）: JDK 25+、Maven 3.9+、Python 3
#  用法:
#    bash scripts/build-release.sh
#    （Windows 也可运行 scripts/build-release.bat）
# ============================================================
set -euo pipefail

# ── 工程根目录 ─────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ── 1. 平台检测 ────────────────────────────────────────────
case "$(uname -s)" in
  Linux*)  PLATFORM="linux" ;;
  Darwin*) PLATFORM="mac" ;;
  MINGW*|MSYS*|CYGWIN*) PLATFORM="win" ;;
  *) echo "[RELEASE] 未知平台: $(uname -s)"; exit 1 ;;
esac
echo "[RELEASE] 目标平台: $PLATFORM"

# ── 2. JDK 探测（jlink 需要完整 JDK 的 jmods）──────────────
# Windows(Git Bash/MSYS) 下，原生程序(java/jlink)无法识别 /d/ 风格路径，
# 统一用 cygpath 转换为 Windows 风格（D:/...）。
to_win() { # to_win <path> → 输出 Windows 风格路径
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}

find_jdk() {
  # 1) JAVA_HOME 有效
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME"; return
  fi
  # 2) 常见位置（Windows Git Bash / 标准 Linux 安装）
  for c in "/d/Program/Java/jdk-25" "/d/Program/Java/jdk-21" \
           "/usr/lib/jvm"/*; do
    [ -e "$c" ] || continue
    if [ -x "$c/bin/java" ] && [ -d "$c/jmods" ]; then echo "$c"; return; fi
  done
  # 3) PATH 中的 java
  if command -v java >/dev/null 2>&1; then
    local jh; jh="$(dirname "$(dirname "$(command -v java)")")"
    [ -d "$jh/jmods" ] && { echo "$jh"; return; }
  fi
  echo ""
}
JDK_HOME="$(find_jdk)"
if [ -z "$JDK_HOME" ] || [ ! -d "$JDK_HOME/jmods" ]; then
  echo "[RELEASE] 错误: 未找到完整 JDK（需要 jmods 目录用于 jlink）。请安装 JDK 25 并设置 JAVA_HOME。"
  exit 1
fi
echo "[RELEASE] JDK: $JDK_HOME"
export JAVA_HOME="$JDK_HOME"

# ── 3. Maven 探测（mvn 不可用时用 java + classworlds 拉起）──
MVN_HOME="${MVN_HOME:-}"
if [ -z "$MVN_HOME" ]; then
  for c in "/d/Program/Maven/apache-maven-3.9.11" "/opt/maven" "/usr/share/maven" \
           "/d/Program/Maven"/*; do
    [ -e "$c" ] || continue
    if ls "$c"/boot/plexus-classworlds-*.jar >/dev/null 2>&1; then
      MVN_HOME="$c"; break
    fi
  done
fi

run_mvn() {
  if [ -n "$MVN_HOME" ] && ls "$MVN_HOME"/boot/plexus-classworlds-*.jar >/dev/null 2>&1; then
    local cw mvn_home_w conf_w
    cw="$(ls "$MVN_HOME"/boot/plexus-classworlds-*.jar | head -1)"
    mvn_home_w="$(to_win "$MVN_HOME")"
    conf_w="$(to_win "$MVN_HOME/bin/m2.conf")"
    "$JDK_HOME/bin/java" -cp "$(to_win "$cw")" \
      "-Dmaven.home=$mvn_home_w" \
      "-Dclassworlds.conf=$conf_w" \
      "-Dmaven.multiModuleProjectDirectory=$(to_win "$ROOT")" \
      --enable-native-access=ALL-UNNAMED \
      org.codehaus.plexus.classworlds.launcher.Launcher "$@"
  elif command -v mvn >/dev/null 2>&1; then
    mvn "$@"
  else
    echo "[RELEASE] 错误: 未找到 Maven。请安装 Maven 3.9+ 或设置 MVN_HOME。"
    exit 1
  fi
}

# ── 4. Python 探测（slim-jar / 组装需要）──────────────────
PY=""
for p in python3 python; do
  if command -v "$p" >/dev/null 2>&1; then PY="$p"; break; fi
done
if [ -z "$PY" ]; then echo "[RELEASE] 错误: 未找到 Python 3"; exit 1; fi
echo "[RELEASE] Python: $PY"

# ── 5. 版本号（读根 pom 项目自身 version，去掉 -SNAPSHOT 用于发布包名）─
# 注意：pom 里第一个 <version> 是 spring-boot parent（如 4.0.6），
#       必须匹配 xuanji-parent 后面的 <version> 才是项目版本。
VERSION="$(awk '/<artifactId>xuanji-parent<\/artifactId>/{f=1} f && /<version>/{sub(/.*<version>/,""); sub(/<\/version>.*/,""); print; exit}' pom.xml)"
if [ -z "$VERSION" ]; then
  echo "[RELEASE] 错误: 无法从 pom.xml 读取项目版本"; exit 1
fi
REL_VERSION="${VERSION%-SNAPSHOT}"
echo "[RELEASE] 版本: $VERSION (发布包 $REL_VERSION)"

# ── 6. Maven 构建 ─────────────────────────────────────────
echo "[RELEASE] 步骤 1/4: mvn clean package ..."
run_mvn -o -q clean package -DskipTests

JAR="xuanji-starter/target/xuanji-starter-$VERSION.jar"
if [ ! -f "$JAR" ]; then echo "[RELEASE] 错误: 构建产物缺失 $JAR"; exit 1; fi

# ── 7. 裁剪 Playwright driver 到目标平台 ──────────────────
echo "[RELEASE] 步骤 2/4: 裁剪 Playwright driver ($PLATFORM) ..."
"$PY" scripts/slim-jar.py "$JAR" --platform "$PLATFORM"

# ── 8. jlink 生成精简运行时 ───────────────────────────────
STAGE="release/xuanji-$REL_VERSION-$PLATFORM"
echo "[RELEASE] 步骤 3/4: jlink 生成内置 JRE ..."
rm -rf "$STAGE" "release/xuanji-$REL_VERSION-$PLATFORM.zip" "release/xuanji-$REL_VERSION-$PLATFORM.tar.gz"
mkdir -p "$STAGE"
JMODS="$JDK_HOME/jmods"
"$JDK_HOME/bin/jlink" --module-path "$(to_win "$JMODS")" --add-modules ALL-MODULE-PATH \
  --strip-debug --no-man-pages --no-header-files --compress=2 \
  --output "$(to_win "$STAGE/jre")"

# ── 9. 组装 + 压缩 ────────────────────────────────────────
echo "[RELEASE] 步骤 4/4: 组装发布包 ..."
"$PY" scripts/assemble-release.py \
  --stage "$STAGE" \
  --jar "$JAR" \
  --version "$REL_VERSION" \
  --platform "$PLATFORM" \
  --plugins-dir plugins \
  --docs-dir docs

echo ""
echo "[RELEASE] 构建完成!"
ls -lh "release/" | grep xuanji
