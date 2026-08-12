#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
璇玑 分平台打包瘦身脚本

裁剪 Spring Boot fat jar 中 Playwright driver-bundle 里非目标平台的 node 驱动。
Playwright 的 driver-bundle jar 内打包了 5 个平台的 node（linux/mac/linux-arm64/mac-arm64
各 ~120MB + win32_x64 93MB），本平台只用到其中一个。此脚本流式重写 fat jar，
把 driver-bundle 内非目标平台的 node 条目剔除，打包体积可降 100MB+。

用法:
    python scripts/slim-jar.py <fat.jar> [--platform win|linux|mac] [--out out.jar]

    --platform  win   → 保留 driver/win32_x64/node.exe
               linux  → 保留 driver/linux/node 和 driver/linux-arm64/node
               mac    → 保留 driver/mac/node 和 driver/mac-arm64/node
               默认按当前系统推断

设计: 纯流式（zipfile 逐条目），不落临时大文件；幂等，可重复执行。
      只处理 fat jar 内 BOOT-INF/lib/driver-bundle-*.jar 条目，其余原样复制。
"""
import argparse
import io
import os
import sys
import zipfile

# 平台 → driver-bundle 内保留的目录
PLATFORM_DIRS = {
    "win":   {"driver/win32_x64"},
    "linux": {"driver/linux", "driver/linux-arm64"},
    "mac":   {"driver/mac", "driver/mac-arm64"},
}


def current_platform():
    if sys.platform.startswith("win"):
        return "win"
    if sys.platform.startswith("darwin"):
        return "mac"
    return "linux"


def slim_driver_bundle(raw: bytes, keep_dirs: set) -> bytes:
    """重写 driver-bundle jar 字节：仅保留指定平台目录，其余原样。"""
    out = io.BytesIO()
    changed = False
    with zipfile.ZipFile(io.BytesIO(raw), "r") as zin, \
         zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            name = info.filename
            top = name.split("/")[0] + ("/" + name.split("/")[1] if "/" in name else "")
            if name.startswith("driver/") and top not in keep_dirs:
                changed = True
                continue  # 跳过非目标平台 node
            zout.writestr(info, zin.read(name))
    if not changed:
        return raw
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser(description="璇玑 fat jar 分平台裁剪")
    parser.add_argument("jar", help="fat jar 路径")
    parser.add_argument("--platform", choices=["win", "linux", "mac"], default=None)
    parser.add_argument("--out", default=None, help="输出路径（默认覆盖原 jar）")
    args = parser.parse_args()

    platform = args.platform or current_platform()
    keep_dirs = PLATFORM_DIRS[platform]
    out_path = args.out or args.jar
    if not os.path.isfile(args.jar):
        print(f"[SLIM-JAR] 未找到 jar: {args.jar}")
        sys.exit(1)

    src_size = os.path.getsize(args.jar)
    print(f"[SLIM-JAR] 平台={platform}, 保留 driver 目录={sorted(keep_dirs)}")
    print(f"[SLIM-JAR] 处理: {args.jar} ({src_size / 1024 / 1024:.0f}MB)")

    tmp_out = out_path + ".slim"
    slimmed_total = 0
    with zipfile.ZipFile(args.jar, "r") as zin, \
         zipfile.ZipFile(tmp_out, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            name = info.filename
            data = zin.read(name)
            if name.startswith("BOOT-INF/lib/driver-bundle-") and name.endswith(".jar"):
                before = len(data)
                new_data = slim_driver_bundle(data, keep_dirs)
                saved = before - len(new_data)
                slimmed_total += saved
                if saved > 0:
                    print(f"[SLIM-JAR]   {name}: {before / 1024 / 1024:.0f}MB → {len(new_data) / 1024 / 1024:.0f}MB (省 {saved / 1024 / 1024:.0f}MB)")
                # 保留原条目时间
                new_info = zipfile.ZipInfo(name, info.date_time)
                # Spring Boot 嵌套 jar（BOOT-INF/lib/*.jar）必须以 STORED 存储，
                # 否则 JarLauncher/NestedJar 无法按偏移随机读取（Playwright 从 classpath 提取 driver 会失败）
                new_info.compress_type = zipfile.ZIP_STORED
                new_info.external_attr = info.external_attr
                zout.writestr(new_info, new_data)
            else:
                zout.writestr(info, data)

    os.replace(tmp_out, out_path)
    dst_size = os.path.getsize(out_path)
    print(f"[SLIM-JAR] 完成: {out_path} ({dst_size / 1024 / 1024:.0f}MB, 共省 {(src_size - dst_size) / 1024 / 1024:.0f}MB)")


if __name__ == "__main__":
    main()
