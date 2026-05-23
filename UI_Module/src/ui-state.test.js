import assert from "node:assert/strict";
import test from "node:test";

import {
  defaultKeyPanelOpen,
  extractFileNameFromPath,
  extractLocalFileSelection,
  hasLocalKeyPair,
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
