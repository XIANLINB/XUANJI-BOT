#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
璇玑 运行时浏览器瘦身脚本

删除 data/browser 下当前平台用不到的浏览器（firefox / webkit / ffmpeg），
只保留 Chromium 相关（完整版 chromium-* + headless_shell），磁盘可省 500MB+。

用法:
    python scripts/slim-browser.py                  # 按当前平台裁剪
    python scripts/slim-browser.py --platform win   # 显式指定平台 (win / linux / mac)
    python scripts/slim-browser.py --dry-run        # 只列出将删除的，不实际删

设计: 幂等，可重复执行；删除目标以 Playwright 目录命名规范为准
      (firefox-*, webkit-*, ffmpeg-*) 其余保留。
"""
import argparse
import os
import shutil
import sys

BROWSER_DIR = os.path.join(os.getcwd(), "data", "browser")

# Playwright 目录命名前缀 → 是否本平台渲染需要
KEEP_PREFIXES = ("chromium", "chromium_headless_shell", "winldd")
DROP_PREFIXES = ("firefox", "webkit", "ffmpeg")


def current_platform() -> str:
    if sys.platform.startswith("win"):
        return "win"
    if sys.platform.startswith("darwin"):
        return "mac"
    return "linux"


def candidates(drop_prefixes):
    if not os.path.isdir(BROWSER_DIR):
        return []
    return [
        name for name in os.listdir(BROWSER_DIR)
        if any(name.startswith(p) for p in drop_prefixes)
    ]


def main():
    parser = argparse.ArgumentParser(description="璇玑运行时浏览器瘦身")
    parser.add_argument("--platform", choices=["win", "linux", "mac"], default=None,
                        help="目标平台（默认按当前系统推断）")
    parser.add_argument("--dry-run", action="store_true", help="只列出不删除")
    args = parser.parse_args()

    platform = args.platform or current_platform()
    print(f"[SLIM] 平台={platform}, 浏览器目录={BROWSER_DIR}")

    if not os.path.isdir(BROWSER_DIR):
        print("[SLIM] 未找到 data/browser，跳过")
        return

    # 本平台用不到 firefox/webkit/ffmpeg（渲染只用 chromium / chromium_headless_shell）
    to_drop = candidates(DROP_PREFIXES)
    keep = [
        name for name in os.listdir(BROWSER_DIR)
        if any(name.startswith(p) for p in KEEP_PREFIXES)
    ]

    total = 0
    for name in to_drop:
        path = os.path.join(BROWSER_DIR, name)
        size = dir_size(path)
        total += size
        if args.dry_run:
            print(f"[SLIM] 将删除: {name} ({fmt(size)})")
        else:
            print(f"[SLIM] 删除: {name} ({fmt(size)})")
            shutil.rmtree(path, ignore_errors=True)

    print(f"[SLIM] 保留: {keep}")
    print(f"[SLIM] 完成，共释放 {fmt(total)}" if not args.dry_run else f"[SLIM] 预览，可释放 {fmt(total)}")


def dir_size(path):
    if not os.path.isdir(path):
        return 0
    total = 0
    for root, _, files in os.walk(path):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(root, f))
            except OSError:
                pass
    return total


def fmt(b):
    return f"{b / 1024 / 1024:.0f}MB" if b > 0 else "0MB"


if __name__ == "__main__":
    main()
