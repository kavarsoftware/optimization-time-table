@echo off
setlocal EnableExtensions DisableDelayedExpansion
pushd "%~dp0.." || exit /b 1
call mvn.cmd clean javafx:run
set "maven_exit=%errorlevel%"
popd
exit /b %maven_exit%
