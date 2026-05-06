@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo [Sifin] Verificando atualizacoes...
git rev-parse --git-dir >nul 2>&1
if %errorlevel% neq 0 goto :launch

for /f "delims=" %%i in ('git rev-parse HEAD 2^>nul') do set LOCAL=%%i

git fetch origin --quiet 2>nul
if %errorlevel% neq 0 (
    echo [Sifin] Sem conexao - iniciando sem verificar atualizacoes.
    goto :launch
)

for /f "delims=" %%i in ('git rev-parse @{upstream} 2^>nul') do set REMOTE=%%i
if "!REMOTE!"=="" (
    for /f "delims=" %%i in ('git rev-parse origin/main 2^>nul') do set REMOTE=%%i
)
if "!REMOTE!"=="" (
    for /f "delims=" %%i in ('git rev-parse origin/master 2^>nul') do set REMOTE=%%i
)

if "!LOCAL!"=="!REMOTE!" (
    echo [Sifin] Versao ja e a mais recente.
    goto :launch
)

echo [Sifin] Nova versao disponivel. Atualizando...
git pull --quiet
echo [Sifin] Recompilando...
mvn package -DskipTests -q
echo [Sifin] Atualizado com sucesso!

:launch
java -Dapp.home="%~dp0" -Dfile.encoding=UTF-8 -jar "%~dp0target\sifin-1.0.0.jar"
