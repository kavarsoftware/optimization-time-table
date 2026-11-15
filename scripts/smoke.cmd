@echo off
cd /d "%~dp0\.."
call mvn -Dsimple.smoke=true javafx:run
