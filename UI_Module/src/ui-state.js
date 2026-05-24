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
