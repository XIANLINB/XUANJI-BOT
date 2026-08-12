#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
璇玑 发布包组装脚本

把「构建产物 + jre + 插件 + 文档」组装成一个开箱即用的发布包目录，并压缩为分发包。

用法:
    python scripts/assemble-release.py \
        --stage release/xuanji-<ver>-<platform> \
        --jar  xuanji-starter/target/xuanji-starter-<ver>.jar \
        --version <ver> \
        --platform win|linux \
        [--plugins-dir plugins] \
        [--docs-dir docs]

产物:
    win    → release/xuanji-<ver>-win.zip
    linux  → release/xuanji-<ver>-linux.tar.gz
"""
import argparse
import os
import shutil
import tarfile
import zipfile

# ── 启动脚本模板 ─────────────────────────────────────────────
WIN_LAUNCH = """@echo off
chcp 65001 >nul
cd /d "%~dp0"
title 璇玑 XuanJi QQ 机器人框架
echo ============================================
echo   璇玑 XuanJi · QQ 机器人框架  v{ver}
echo   控制台: http://localhost:8668/xuanji/console/
echo   退出请直接关闭本窗口
echo ============================================
jre\\bin\\java.exe -jar lib\\{jar}
echo.
echo [璇玑] 框架已退出，按任意键关闭...
pause >nul
"""

LINUX_LAUNCH = """#!/usr/bin/env bash
# 璇玑 XuanJi QQ 机器人框架  v{ver}
cd "$(dirname "$0")"
echo "============================================"
echo "  璇玑 XuanJi · QQ 机器人框架  v{ver}"
echo "  控制台: http://localhost:8668/xuanji/console/"
echo "  退出请按 Ctrl+C"
echo "============================================"
exec ./jre/bin/java -jar "lib/{jar}"
"""

README_TXT = """璇玑 XuanJi · QQ 机器人框架  v{ver}
========================================

【快速开始】
1. 启动：
   Windows → 双击「启动.bat」
   Linux   → 执行 ./start.sh
2. 浏览器打开  http://localhost:8668/xuanji/console/
3. 首次访问进入引导流程：
   设置管理口令(PIN) → 添加 QQ 机器人(AppID/AppSecret/Token) → 完成
4. 机器人自动上线，群里即可使用。

【目录说明】
  jre/      内置 Java 运行时（无需安装 JDK）
  lib/      框架主程序
  plugins/  插件目录（.jar 放这里，控制台「插件管理」热加载生效）
  data/     运行数据（自动生成；备份请整体打包本目录）
  logs/     运行日志
  docs/     完整文档

【详细文档】docs/ 目录：
  使用手册.md               日常使用、控制台指南、常见问题
  插件开发完整指南.md        插件开发（@Command/事件/动作/注解）
  架构说明.md               架构设计（模块/管线/存储/AI/安全）
  QQ内嵌键盘按钮使用指南.md  内嵌键盘按钮开发

【默认端口】8668，可用环境变量 SERVER_PORT 修改（如 SERVER_PORT=9000）

【交流】Java开发交流群：534445438
"""


def write_file(path, content):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def assemble(args):
    stage = args.stage
    jar_name = os.path.basename(args.jar)
    platform = args.platform
    ver = args.version

    # 1. 目录骨架
    for sub in ("lib", "plugins", "docs"):
        os.makedirs(os.path.join(stage, sub), exist_ok=True)

    # 2. 主程序 jar
    dst_jar = os.path.join(stage, "lib", jar_name)
    if os.path.abspath(dst_jar) != os.path.abspath(args.jar):
        shutil.copy2(args.jar, dst_jar)

    # 3. 插件（存在才复制）
    if args.plugins_dir and os.path.isdir(args.plugins_dir):
        for name in sorted(os.listdir(args.plugins_dir)):
            if name.endswith(".jar"):
                shutil.copy2(os.path.join(args.plugins_dir, name),
                             os.path.join(stage, "plugins", name))

    # 4. 文档
    if args.docs_dir and os.path.isdir(args.docs_dir):
        for name in os.listdir(args.docs_dir):
            src = os.path.join(args.docs_dir, name)
            if os.path.isfile(src):
                shutil.copy2(src, os.path.join(stage, "docs", name))
            elif os.path.isdir(src):  # 子目录（如 docs/协议）
                shutil.copytree(src, os.path.join(stage, "docs", name), dirs_exist_ok=True)

    # 5. 启动脚本 + README
    write_file(os.path.join(stage, "启动.bat"), WIN_LAUNCH.format(ver=ver, jar=jar_name))
    write_file(os.path.join(stage, "start.sh"), LINUX_LAUNCH.format(ver=ver, jar=jar_name))
    write_file(os.path.join(stage, "README.txt"), README_TXT.format(ver=ver))

    # 6. 压缩分发包
    release_dir = os.path.dirname(stage) or "."
    os.makedirs(release_dir, exist_ok=True)
    if platform == "win":
        out = os.path.join(release_dir, f"xuanji-{ver}-win.zip")
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
            for root, _, files in os.walk(stage):
                for f in files:
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, os.path.dirname(stage))
                    zf.write(full, rel)
        print(f"[ASSEMBLE] 已生成: {out}")
    else:
        out = os.path.join(release_dir, f"xuanji-{ver}-{platform}.tar.gz")
        with tarfile.open(out, "w:gz") as tf:
            tf.add(stage, arcname=os.path.basename(stage))
        print(f"[ASSEMBLE] 已生成: {out}")

    size_mb = os.path.getsize(out) / 1024 / 1024
    print(f"[ASSEMBLE] 体积: {size_mb:.1f} MB")


def main():
    p = argparse.ArgumentParser(description="璇玑发布包组装")
    p.add_argument("--stage", required=True, help="发布包目录")
    p.add_argument("--jar", required=True, help="fat jar 路径")
    p.add_argument("--version", required=True, help="版本号")
    p.add_argument("--platform", required=True, choices=["win", "linux"])
    p.add_argument("--plugins-dir", default="plugins")
    p.add_argument("--docs-dir", default="docs")
    args = p.parse_args()
    assemble(args)


if __name__ == "__main__":
    main()
