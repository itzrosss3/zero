@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Instalador de la voz realista de Zero (Chatterbox)
echo.
echo  ==================================================
echo     VOZ REALISTA DE ZERO (Chatterbox)
echo  ==================================================
echo.

echo [1/5] Buscando Python 3.11...
py -3.11 --version >nul 2>&1
if errorlevel 1 goto error_python

echo [2/5] Creando su entorno de Python...
set "VENV=%LOCALAPPDATA%\Zero\venv-chatterbox"
if not exist "%VENV%\Scripts\python.exe" py -3.11 -m venv "%VENV%"
if errorlevel 1 goto error_general
"%VENV%\Scripts\python.exe" -m pip install --upgrade pip

echo [3/5] Instalando Chatterbox, puede tardar bastante...
"%VENV%\Scripts\python.exe" -m pip install chatterbox-tts
if errorlevel 1 goto error_general

echo [4/5] Instalando PyTorch para tu grafica RTX 50 (unos 3 GB)...
echo       Si al final sale un aviso de "chatterbox-tts requires torch==..." es normal.
"%VENV%\Scripts\python.exe" -m pip install torch==2.7.1+cu128 torchaudio==2.7.1+cu128 --index-url https://download.pytorch.org/whl/cu128 --extra-index-url https://pypi.org/simple
if errorlevel 1 goto error_general

echo [5/5] Descargando el modelo de voz (unos 3 GB) y probando...
"%VENV%\Scripts\python.exe" voz_realista.py --precargar
if errorlevel 1 goto error_general

echo.
echo  Descargando el cerebro ligero para que todo quepa en la grafica...
ollama pull qwen3:4b

echo.
echo  ==================================================
echo     LISTO. Abre Zero y elige una voz "Chatterbox".
echo  ==================================================
pause
exit /b 0

:error_python
echo.
echo  No encuentro Python 3.11 (Chatterbox no funciona con 3.12).
echo  Instalalo desde: https://www.python.org/downloads/release/python-3119/
echo  "Windows installer (64-bit)". NO marques "Add python.exe to PATH":
echo  asi tu Python 3.12 sigue siendo el principal. Despues vuelve a abrir esto.
pause
exit /b 1

:error_general
echo.
echo  Algo ha fallado. Copia el error de arriba para revisarlo.
pause
exit /b 1
