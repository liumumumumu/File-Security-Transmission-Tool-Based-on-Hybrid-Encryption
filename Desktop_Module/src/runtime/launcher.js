const fs = require("node:fs/promises");
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
    stdio: ["pipe", "ignore", "ignore"],
  };
}

async function ensureRuntimeDirectories(runtimePaths) {
  await Promise.all([
    fs.mkdir(runtimePaths.userDataDir, { recursive: true }),
    fs.mkdir(runtimePaths.keyDir, { recursive: true }),
    fs.mkdir(runtimePaths.logDir, { recursive: true }),
    fs.mkdir(runtimePaths.downloadDir, { recursive: true }),
  ]);
}

function spawnManagedProcess(command, args, options = {}) {
  const child = spawn(command, args, {
    windowsHide: true,
    stdio: "ignore",
    ...options,
  });
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
  buildJavaArguments,
  buildJavaProcessOptions,
  ensureRuntimeDirectories,
  spawnManagedProcess,
  stopManagedProcess,
  waitForServiceReady,
};

