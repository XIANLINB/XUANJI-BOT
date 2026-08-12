@echo off
rem ============================================================
rem  璇玑 XuanJi · Windows 发布包构建入口
rem  直接运行本脚本即可产出 release\xuanji-<版本>-win.zip
rem  （依赖 Git Bash 的 bash 命令；若报错请用 bash scripts\build-release.sh）
rem ============================================================
setlocal
chcp 65001 >nul
cd /d "%~dp0.."
echo [RELEASE] 开始构建 Windows 发布包...
bash scripts\build-release.sh
if errorlevel 1 (
    echo [RELEASE] 构建失败，请检查上方日志。
    pause
    exit /b 1
)
pause
