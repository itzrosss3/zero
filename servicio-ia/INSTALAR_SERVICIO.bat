@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Instalador del servicio de IA de Zero
echo.
echo  ==================================================
echo     SERVICIO DE IA DE ZERO (oido y voz)
echo  ==================================================
echo.

echo [1/5] Buscando Python...
set "PY="
py -3.12 --version >nul 2>&1 && set "PY=py -3.12"
if not defined PY py -3.11 --version >nul 2>&1 && set "PY=py -3.11"
if not defined PY py -3.13 --version >nul 2>&1 && set "PY=py -3.13"
if not defined PY goto error_python
echo       Encontrado: %PY%

echo [2/5] Creando el entorno de Python (fuera del proyecto, para no llenar Eclipse)...
set "VENV=%LOCALAPPDATA%\Zero\venv"
if not exist "%VENV%\Scripts\python.exe" %PY% -m venv "%VENV%"
if errorlevel 1 goto error_general

echo [3/5] Instalando librerias, puede tardar unos minutos...
"%VENV%\Scripts\python.exe" -m pip install --upgrade pip
"%VENV%\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto error_general

echo [4/5] Preparando Ollama...
where ollama >nul 2>&1
if errorlevel 1 goto error_ollama
setx OLLAMA_FLASH_ATTENTION 1 >nul
setx OLLAMA_KV_CACHE_TYPE q8_0 >nul
ollama pull qwen3:8b
if errorlevel 1 goto error_general

echo [5/5] Descargando el oido y la voz...
"%VENV%\Scripts\python.exe" servicio.py --precargar
if errorlevel 1 goto error_general

echo.
echo  ==================================================
echo     LISTO. Ahora abre el proyecto en Eclipse.
echo  ==================================================
pause
exit /b 0

:error_python
echo.
echo  No encuentro Python 3.12. Instalalo desde
echo  https://www.python.org/downloads/release/python-31210/
echo  marcando "Add python.exe to PATH".
pause
exit /b 1

:error_ollama
echo.
echo  No encuentro Ollama. Instalalo desde https://ollama.com/download
pause
exit /b 1

:error_general
echo.
echo  Algo ha fallado. Copia el error de arriba para revisarlo.
pause
exit /b 1
