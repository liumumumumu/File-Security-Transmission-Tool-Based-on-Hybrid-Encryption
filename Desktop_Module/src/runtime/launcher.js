const fs = require("node:fs");
const fsPromises = require("node:fs/promises");
const { spawn } = require("node:child_process");

function buildJavaArguments(jarPath) {
  return [
    "-jar",
    jarPath,
    "--app.role=client",
    "--spring.profiles.active=client",
    "--server.tcp.enabled=false",
  ];
}

function buildJavaProcessOptions(runtimePaths, env) {
  return {
    cwd: runtimePaths.tcpClientDir,
    env,
    // Keep stdin open so the bundled Java console loop does not treat
    // Electron startup as a closed terminal and shut Spring down.
    stdio: ["pipe", "pipe", "pipe"],
  };
}

async function ensureRuntimeDirectories(runtimePaths) {
  await Promise.all([
    fsPromises.mkdir(runtimePaths.userDataDir, { recursive: true }),
    fsPromises.mkdir(runtimePaths.keyDir, { recursive: true }),
    fsPromises.mkdir(runtimePaths.logDir, { recursive: true }),
    fsPromises.mkdir(runtimePaths.downloadDir, { recursive: true }),
  ]);
}

function attachProcessLogging(child, logFilePath, label = "process") {
  if (!child || !logFilePath) {
    return null;
  }

  const stream = fs.createWriteStream(logFilePath, { flags: "a" });
  const prefix = `[${new Date().toISOString()}] ${label}`;
  stream.write(`${prefix} started pid=${child.pid || "unknown"}\n`);

  child.stdout?.on("data", (chunk) => stream.write(chunk));
  child.stderr?.on("data", (chunk) => stream.write(chunk));
  child.on("error", (error) => {
    stream.write(`\n${prefix} error: ${error.message}\n`);
  });
  child.on("exit", (code, signal) => {
    stream.write(`\n${prefix} exited code=${code ?? ""} signal=${signal ?? ""}\n`);
    stream.end();
  });

  return stream;
}

function spawnManagedProcess(command, args, options = {}) {
  const { logFilePath, logLabel, ...spawnOptions } = options;
  const child = spawn(command, args, {
    windowsHide: true,
    stdio: logFilePath ? ["ignore", "pipe", "pipe"] : "ignore",
    ...spawnOptions,
  });
  attachProcessLogging(child, logFilePath, logLabel || command);
  child.unref();
  return child;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForServiceReady({
  url,
  timeoutMs = 30_000,
  intervalMs = 500,
  fetchImpl = global.fetch,
}) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;

  while (Date.now() < deadline) {
    try {
      const response = await fetchImpl(url);
      if (response.ok) {
        const payload = await response.json();
        if (payload && payload.status === "UP") {
          return payload;
        }
      }
    } catch (error) {
      lastError = error;
    }
    await sleep(intervalMs);
  }

  const detail = lastError ? ` Last error: ${lastError.message}` : "";
  throw new Error(`Service at ${url} did not become ready within ${timeoutMs}ms.${detail}`);
}

async function stopManagedProcess(childProcess) {
  if (!childProcess || childProcess.killed) {
    return;
  }

  if (process.platform === "win32") {
    await new Promise((resolve) => {
      const killer = spawn("taskkill", [
        "/pid",
        String(childProcess.pid),
        "/t",
        "/f",
      ], {
        windowsHide: true,
        stdio: "ignore",
      });
      killer.on("error", resolve);
      killer.on("close", resolve);
    });
    return;
  }

  try {
    childProcess.kill("SIGTERM");
  } catch (error) {
    // Best-effort shutdown.
  }
}

module.exports = {
  attachProcessLogging,
  buildJavaArguments,
  buildJavaProcessOptions,
  ensureRuntimeDirectories,
  spawnManagedProcess,
  stopManagedProcess,
  waitForServiceReady,
};

