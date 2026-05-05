@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

if "%JAR_NAME%"=="" set "JAR_NAME=FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar"
if "%JAR_PATH%"=="" set "JAR_PATH=%PROJECT_DIR%\target\%JAR_NAME%"
if "%CONFIG_PATH%"=="" set "CONFIG_PATH=%PROJECT_DIR%\application-server.yml"

if not exist "%JAR_PATH%" (
  if exist "%SCRIPT_DIR%%JAR_NAME%" set "JAR_PATH=%SCRIPT_DIR%%JAR_NAME%"
)

if not exist "%JAR_PATH%" (
  echo Jar not found: %JAR_PATH%
  echo Run "mvn clean package" first, or set JAR_PATH to the packaged jar.
  pause
  exit /b 1
)

if "%SERVER_TCP_ENABLED%"=="" set "SERVER_TCP_ENABLED=true"
if "%SERVER_TCP_BIND_HOST%"=="" set "SERVER_TCP_BIND_HOST=0.0.0.0"
if "%SERVER_TCP_BIND_PORT%"=="" set "SERVER_TCP_BIND_PORT=9000"
if "%SERVER_HTTP_ADDRESS%"=="" set "SERVER_HTTP_ADDRESS=0.0.0.0"
if "%SERVER_HTTP_PORT%"=="" set "SERVER_HTTP_PORT=8080"
if "%NODE_DEVICE_ID%"=="" set "NODE_DEVICE_ID=server-node"

set "APP_ARGS=--server.tcp.enabled=%SERVER_TCP_ENABLED% --server.tcp.bind-host=%SERVER_TCP_BIND_HOST% --server.tcp.bind-port=%SERVER_TCP_BIND_PORT% --server.address=%SERVER_HTTP_ADDRESS% --server.port=%SERVER_HTTP_PORT% --node.device-id=%NODE_DEVICE_ID% --node.auto-connect=false"

if exist "%CONFIG_PATH%" (
  set "APP_ARGS=--spring.config.location=file:%CONFIG_PATH% %APP_ARGS%"
)

java %JAVA_OPTS% -jar "%JAR_PATH%" %APP_ARGS%
pause
