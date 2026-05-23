const assert = require("node:assert/strict");
const test = require("node:test");

const packageJson = require("../package.json");

test("electron-builder config includes a Windows icon path", () => {
  assert.ok(packageJson.build);
  assert.equal(packageJson.build.win.target[0], "msi");
  assert.ok(packageJson.build.win.icon);
});
