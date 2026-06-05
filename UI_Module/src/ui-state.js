function toBoolean(value) {
  return value === true || value === "true";
}

export function hasLocalKeyPair(keyStatus) {
  if (!keyStatus) return false;
  return toBoolean(keyStatus.hasPrivateKey) && toBoolean(keyStatus.hasPublicKey);
}

export function defaultKeyPanelOpen(keyStatus) {
  return !hasLocalKeyPair(keyStatus);
}

export function extractFileNameFromPath(filePath) {
  const text = String(filePath || "").trim();
  if (!text) return "";
  return text.split(/[\\/]/).pop() || "";
}

export function extractLocalFileSelection(file) {
  if (!file) return null;
  const path = String(file.path || "").trim();
  if (!path) return null;
  return {
    name: String(file.name || extractFileNameFromPath(path)),
    path,
  };
}

export function toFriendlyErrorMessage(message) {
  const text = String(message || "");
  if (!text) return "";
  if (text.includes("Key pair already exists")) {
    return "本机已有密钥，无需重复生成。如需更换身份，请先删除旧密钥。";
  }
  if (text.includes("privateKey or privateKeyPath is required")) {
    return "请先选择私钥文件或粘贴私钥内容。";
  }
  if (text.includes("Private key not found")) {
    return "当前没有可用密钥，请先生成或导入私钥。";
  }
  if (text.includes("Failed to fetch") || text.includes("后端返回非 JSON")) {
    return "服务没有正常响应，请确认本机服务已启动。";
  }
  if (text.includes("Internal Server Error") || text.includes("HTTP 500")) {
    return "服务暂时不可用，请稍后重试或检查本机服务。";
  }
  return text;
}

export function shortId(value, length = 12) {
  if (!value) return "--";
  const text = String(value);
  if (text.length <= length) return text;
  return `${text.slice(0, length)}...`;
}

export function taskIdentifier(task) {
  return task?.transferId || task?.taskId || "";
}

export function isTerminalTaskStatus(status) {
  return ["COMPLETED", "FAILED", "CANCELED", "CANCELLED", "REJECTED"].includes(String(status || "").toUpperCase());
}

export function isActiveReceiveTask(task) {
  return String(task?.direction || "").toUpperCase() === "RECEIVE" && !isTerminalTaskStatus(task?.status);
}

export function isCompletedReceiveTask(task) {
  return String(task?.direction || "").toUpperCase() === "RECEIVE" && String(task?.status || "").toUpperCase() === "COMPLETED";
}

function taskCreatedAtMillis(task) {
  const millis = Date.parse(task?.createdAt || "");
  return Number.isFinite(millis) ? millis : 0;
}

export function receiveHistoryTasks(tasks, options = {}) {
  if (!Array.isArray(tasks)) return [];
  const limit = Number(options.limit || 3);
  const sorted = tasks.filter(isCompletedReceiveTask).sort((left, right) => taskCreatedAtMillis(right) - taskCreatedAtMillis(left));
  return options.expanded ? sorted : sorted.slice(0, limit);
}

export function taskSpeedText(task) {
  if (task?.speedText) return task.speedText;
  const speed = Number(task?.speedMegabytesPerSecond || 0);
  return `${speed.toFixed(2)} MB/s`;
}

export function normalizeRetransmitRequests(value) {
  if (!Array.isArray(value)) return [];
  return value.filter((request) => request && request.transferId);
}

export function hasDesktopDebugApi(desktopApi) {
  return Boolean(
    desktopApi?.openDevTools &&
      desktopApi?.openLogsFolder &&
      desktopApi?.getDebugInfo &&
      desktopApi?.openSystemStatus,
  );
}
