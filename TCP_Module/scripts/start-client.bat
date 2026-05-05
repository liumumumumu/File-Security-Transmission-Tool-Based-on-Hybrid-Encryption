@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

if "%JAR_NAME%"=="" set "JAR_NAME=FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar"
if "%JAR_PATH%"=="" set "JAR_PATH=%PROJECT_DIR%\target\%JAR_NAME%"
if "%CONFIG_PATH%"=="" set "CONFIG_PATH=%PROJECT_DIR%\application-client.yml"

if not exist "%JAR_PATH%" (
  if exist "%SCRIPT_DIR%%JAR_NAME%" set "JAR_PATH=%SCRIPT_DIR%%JAR_NAME%"
)

if not exist "%JAR_PATH%" (
  echo Jar not found: %JAR_PATH%
  echo Run "mvn clean package" first, or set JAR_PATH to the packaged jar.
  pause
  exit /b 1
)

if "%CLIENT_HTTP_ADDRESS%"=="" set "CLIENT_HTTP_ADDRESS=127.0.0.1"
if "%CLIENT_HTTP_PORT%"=="" set "CLIENT_HTTP_PORT=8081"
if "%NODE_DEVICE_ID%"=="" set "NODE_DEVICE_ID=client-device-1"
if "%NODE_AUTO_CONNECT%"=="" set "NODE_AUTO_CONNECT=true"
if "%CLIENT_SERVER_HOST%"=="" set "CLIENT_SERVER_HOST=127.0.0.1"
if "%CLIENT_SERVER_PORT%"=="" set "CLIENT_SERVER_PORT=9000"
if "%TRANSFER_RECEIVE_DIR%"=="" set "TRANSFER_RECEIVE_DIR=downloads-client-1"

set "APP_ARGS=--server.tcp.enabled=false --server.address=%CLIENT_HTTP_ADDRESS% --server.port=%CLIENT_HTTP_PORT% --node.device-id=%NODE_DEVICE_ID% --node.auto-connect=%NODE_AUTO_CONNECT% --client.serverHost=%CLIENT_SERVER_HOST% --client.serverPort=%CLIENT_SERVER_PORT% --transfer.receive-dir=%TRANSFER_RECEIVE_DIR%"

if exist "%CONFIG_PATH%" (
  set "APP_ARGS=--spring.config.location=file:%CONFIG_PATH% %APP_ARGS%"
)

java %JAVA_OPTS% -jar "%JAR_PATH%" %APP_ARGS%
pause
