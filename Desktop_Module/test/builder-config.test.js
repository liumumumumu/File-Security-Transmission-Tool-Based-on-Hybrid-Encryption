const assert = require("node:assert/strict");
const test = require("node:test");

const packageJson = require("../package.json");

test("electron-builder config includes Windows and macOS targets", () => {
  assert.ok(packageJson.build);
  assert.equal(packageJson.build.win.target[0], "msi");
  assert.ok(packageJson.build.win.icon);
  assert.deepEqual(packageJson.build.mac.target, ["dmg", "zip"]);
  assert.equal(packageJson.build.mac.identity, null);
});
