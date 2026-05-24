const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildJavaArguments,
  waitForServiceReady,
} = require("../src/runtime/launcher");

test("builds fixed java client launch arguments", () => {
  assert.deepEqual(buildJavaArguments("C:\\runtime\\tcp-client\\app.jar"), [
    "-jar",
    "C:\\runtime\\tcp-client\\app.jar",
    "--app.role=client",
    "--spring.profiles.active=client",
    "--server.tcp.enabled=false",
  ]);
});

test("waits until health endpoint reports UP", async () => {
  let attempt = 0;

  await waitForServiceReady({
    url: "http://127.0.0.1:20201/api/system/status",
    timeoutMs: 1000,
    intervalMs: 1,
    fetchImpl: async () => {
      attempt += 1;
      if (attempt < 3) {
        throw new Error("not ready");
      }
      return {
        ok: true,
        json: async () => ({ status: "UP" }),
      };
    },
  });

  assert.equal(attempt, 3);
});

test("fails when health endpoint never becomes ready", async () => {
  await assert.rejects(
    waitForServiceReady({
      url: "http://127.0.0.1:20201/api/system/status",
      timeoutMs: 20,
      intervalMs: 1,
      fetchImpl: async () => ({
        ok: true,
        json: async () => ({ status: "STARTING" }),
      }),
    }),
    /did not become ready/i,
  );
});

