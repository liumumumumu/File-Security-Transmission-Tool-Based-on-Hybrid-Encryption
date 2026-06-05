import assert from "node:assert/strict";
import test from "node:test";

import {
  defaultKeyPanelOpen,
  extractFileNameFromPath,
  extractLocalFileSelection,
  hasLocalKeyPair,
  hasDesktopDebugApi,
  isActiveReceiveTask,
  isCompletedReceiveTask,
  isTerminalTaskStatus,
  normalizeRetransmitRequests,
  receiveHistoryTasks,
  shortId,
  taskIdentifier,
  taskSpeedText,
  toFriendlyErrorMessage,
} from "./ui-state.js";

test("hasLocalKeyPair returns true when both key flags are present", () => {
  assert.equal(hasLocalKeyPair({ hasPrivateKey: "true", hasPublicKey: "true" }), true);
  assert.equal(hasLocalKeyPair({ hasPrivateKey: true, hasPublicKey: true }), true);
});

test("hasLocalKeyPair returns false when either key flag is missing", () => {
  assert.equal(hasLocalKeyPair({ hasPrivateKey: "true", hasPublicKey: "false" }), false);
  assert.equal(hasLocalKeyPair({ hasPrivateKey: false, hasPublicKey: true }), false);
  assert.equal(hasLocalKeyPair(null), false);
});

test("defaultKeyPanelOpen keeps key setup visible until key pair exists", () => {
  assert.equal(defaultKeyPanelOpen({ hasPrivateKey: "false", hasPublicKey: "false" }), true);
  assert.equal(defaultKeyPanelOpen({ hasPrivateKey: "true", hasPublicKey: "true" }), false);
});

test("extractFileNameFromPath supports windows and unix paths", () => {
  assert.equal(extractFileNameFromPath("C:\\Users\\15328\\Desktop\\demo.zip"), "demo.zip");
  assert.equal(extractFileNameFromPath("/home/mfxian/demo.zip"), "demo.zip");
  assert.equal(extractFileNameFromPath(""), "");
});

test("extractLocalFileSelection reads Electron style dropped file paths", () => {
  assert.deepEqual(
    extractLocalFileSelection({
      name: "demo.zip",
      path: "C:\\Users\\15328\\Desktop\\demo.zip",
    }),
    {
      name: "demo.zip",
      path: "C:\\Users\\15328\\Desktop\\demo.zip",
    },
  );
});

test("extractLocalFileSelection returns null when browser drop has no local path", () => {
  assert.equal(
    extractLocalFileSelection({
      name: "demo.zip",
      path: "",
    }),
    null,
  );
});

test("shortId keeps compact task identifiers readable", () => {
  assert.equal(shortId("transfer-abcdef123456", 12), "transfer-abc...");
  assert.equal(shortId("short", 12), "short");
  assert.equal(shortId("", 12), "--");
});

test("taskIdentifier prefers transferId over taskId", () => {
  assert.equal(taskIdentifier({ transferId: "transfer-1", taskId: "task-1" }), "transfer-1");
  assert.equal(taskIdentifier({ taskId: "task-1" }), "task-1");
});

test("active receive tasks exclude terminal statuses", () => {
  assert.equal(isTerminalTaskStatus("REJECTED"), true);
  assert.equal(isTerminalTaskStatus("CANCELLED"), true);
  assert.equal(isActiveReceiveTask({ direction: "RECEIVE", status: "TRANSFERRING" }), true);
  assert.equal(isActiveReceiveTask({ direction: "RECEIVE", status: "REJECTED" }), false);
  assert.equal(isActiveReceiveTask({ direction: "SEND", status: "TRANSFERRING" }), false);
});

test("completed receive history includes only successful receive tasks", () => {
  assert.equal(isCompletedReceiveTask({ direction: "RECEIVE", status: "COMPLETED" }), true);
  assert.equal(isCompletedReceiveTask({ direction: "SEND", status: "COMPLETED" }), false);
  assert.equal(isCompletedReceiveTask({ direction: "RECEIVE", status: "FAILED" }), false);
  assert.equal(isCompletedReceiveTask({ direction: "RECEIVE", status: "CANCELED" }), false);
  assert.equal(isCompletedReceiveTask({ direction: "RECEIVE", status: "TRANSFERRING" }), false);
});

test("receiveHistoryTasks defaults to the latest three completed receive tasks", () => {
  const tasks = [
    { transferId: "oldest", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-01T10:00:00Z" },
    { transferId: "send", direction: "SEND", status: "COMPLETED", createdAt: "2026-06-05T10:00:00Z" },
    { transferId: "failed", direction: "RECEIVE", status: "FAILED", createdAt: "2026-06-05T09:00:00Z" },
    { transferId: "canceled", direction: "RECEIVE", status: "CANCELED", createdAt: "2026-06-05T08:00:00Z" },
    { transferId: "active", direction: "RECEIVE", status: "TRANSFERRING", createdAt: "2026-06-05T07:00:00Z" },
    { transferId: "third", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-03T10:00:00Z" },
    { transferId: "second", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-04T10:00:00Z" },
    { transferId: "newest", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-05T10:00:00Z" },
  ];

  assert.deepEqual(
    receiveHistoryTasks(tasks).map((task) => task.transferId),
    ["newest", "second", "third"],
  );
});

test("receiveHistoryTasks can expand to all completed receive tasks", () => {
  const tasks = [
    { transferId: "older", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-01T10:00:00Z" },
    { transferId: "third", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-03T10:00:00Z" },
    { transferId: "second", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-04T10:00:00Z" },
    { transferId: "newest", direction: "RECEIVE", status: "COMPLETED", createdAt: "2026-06-05T10:00:00Z" },
  ];

  assert.deepEqual(
    receiveHistoryTasks(tasks, { expanded: true }).map((task) => task.transferId),
    ["newest", "second", "third", "older"],
  );
});

test("taskSpeedText uses backend text and formats numeric fallback", () => {
  assert.equal(taskSpeedText({ speedText: "3.20 MB/s", speedMegabytesPerSecond: 1 }), "3.20 MB/s");
  assert.equal(taskSpeedText({ speedMegabytesPerSecond: 1.236 }), "1.24 MB/s");
  assert.equal(taskSpeedText({}), "0.00 MB/s");
});

test("normalizeRetransmitRequests keeps only usable pending requests", () => {
  assert.deepEqual(
    normalizeRetransmitRequests([{ transferId: "transfer-1" }, null, { startBlockId: 2 }]),
    [{ transferId: "transfer-1" }],
  );
  assert.deepEqual(normalizeRetransmitRequests(null), []);
});

test("hasDesktopDebugApi requires every debug bridge method", () => {
  assert.equal(
    hasDesktopDebugApi({
      openDevTools() {},
      openLogsFolder() {},
      getDebugInfo() {},
      openSystemStatus() {},
    }),
    true,
  );
  assert.equal(hasDesktopDebugApi({ openDevTools() {} }), false);
  assert.equal(hasDesktopDebugApi(null), false);
});

test("toFriendlyErrorMessage translates existing key conflicts into user-facing copy", () => {
  assert.equal(
    toFriendlyErrorMessage('Crypto service POST failed: {"success":"false","error":"Key pair already exists"}'),
    "本机已有密钥，无需重复生成。如需更换身份，请先删除旧密钥。",
  );
});

test("toFriendlyErrorMessage keeps fetch failures as service availability guidance", () => {
  assert.equal(
    toFriendlyErrorMessage("Failed to fetch"),
    "服务没有正常响应，请确认本机服务已启动。",
  );
});

test("toFriendlyErrorMessage translates missing private key into recovery guidance", () => {
  assert.equal(
    toFriendlyErrorMessage("Private key not found"),
    "当前没有可用密钥，请先生成或导入私钥。",
  );
});
