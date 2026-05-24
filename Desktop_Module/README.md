# Desktop Module

`Desktop_Module` is the Windows desktop entrypoint for the client MSI.

It is responsible for:

- packaging the final Electron MSI
- bundling the local Java client runtime
- bundling the local crypto service runtime
- starting both local processes before loading the UI
- cleaning up child processes when Electron exits

## Windows packaging flow

Build the repo artifacts first:

```powershell
cd ..\UI_Module
npm install
npm run build:static

cd ..\TCP_Module
mvn clean package

cd ..\Encryption_Module_OpenSSLversion\Xcode_solution\deploy
.\package-windows.ps1
```

Then prepare and package the desktop runtime:

```powershell
cd ..\..\..\Desktop_Module
npm install
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
npm run dist:win-msi
```

The packaging command copies these runtime inputs into `Desktop_Module\build\runtime\`:

- `TCP_Module\target\*.jar`
- `Encryption_Module_OpenSSLversion\Xcode_solution\dist\crypto-service-windows-x64\`
- `TCP_Module\src\main\resources\application-client.yml`
- `%JAVA_HOME%`

Electron Builder then emits the MSI into `Desktop_Module\release\`.

## Development mode

Development mode has two useful preview paths on Windows.

### Preview the Electron shell only

This is the fastest way to check the desktop window, title bar, icon, sidebar, settings drawer, and current UI styling.

```powershell
cd ..\UI_Module
npm install
npm run dev

cd ..\Desktop_Module
npm install
npm run dev
```

In this mode Electron opens:

```text
http://127.0.0.1:5173/
```

You do not need to manually start the Java client for this shell-only preview.

### Preview Electron against a local Java client

If you want the Electron window to load the local Java client page instead of the Vite dev server, build the frontend static files, build the Java jar, start the local Java client yourself, and then point Electron at `127.0.0.1:20201`.

```powershell
cd ..\UI_Module
npm install
npm run build:static

cd ..\TCP_Module
mvn clean package
```

Then start your local Java client with your existing Windows start script or jar command so it serves:

```text
http://127.0.0.1:20201/
```

After that, run Electron with:

```powershell
cd ..\Desktop_Module
$env:DESKTOP_RENDERER_URL="http://127.0.0.1:20201/"
npm run dev
```

In this mode:

- Electron is still running in development mode
- the page content comes from the local Java client instead of Vite
- you must start the Java client yourself before launching Electron

### Important packaging note

For the final MSI build, you do **not** keep a Java startup script running in the background. You only need to build the Java and crypto runtime artifacts first:

```powershell
cd ..\UI_Module
npm install
npm run build:static

cd ..\TCP_Module
mvn clean package

cd ..\Encryption_Module_OpenSSLversion\Xcode_solution\deploy
.\package-windows.ps1

cd ..\..\..\Desktop_Module
npm install
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
npm run dist:win-msi
```

After installation, the packaged Electron app starts the bundled Java client and crypto service automatically.

## Renderer override

You can still point development Electron at an already running local Java client with:

```powershell
$env:DESKTOP_RENDERER_URL="http://127.0.0.1:20201/"
npm run dev
```

## Runtime layout

Packaged Electron expects:

- `resources\runtime\tcp-client\app.jar`
- `resources\runtime\crypto-service\crypto-service.exe`
- `resources\runtime\crypto-service\libssl-3-x64.dll`
- `resources\runtime\crypto-service\libcrypto-3-x64.dll`
- `resources\runtime\config\application-client.yml`
- `resources\runtime\jre\`

Packaged runtime data is written under:

- `%LOCALAPPDATA%\FileSecurityTransmission\`
- `%LOCALAPPDATA%\FileSecurityTransmission\crypto_keys\`
- `%LOCALAPPDATA%\FileSecurityTransmission\logs\`
- `%USERPROFILE%\Downloads\FileSecurityTransmission\`
