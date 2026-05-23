const assert = require("node:assert/strict");
const test = require("node:test");

const {
  createWindowOptions,
  getRendererUrl,
  getWindowIconPath,
} = require("../src/desktop-config");

test("uses the Vue dev server by default", () => {
  assert.equal(getRendererUrl({}), "http://127.0.0.1:5173/");
});

test("allows the renderer URL to be overridden", () => {
  assert.equal(
    getRendererUrl({ DESKTOP_RENDERER_URL: "http://127.0.0.1:20201/" }),
    "http://127.0.0.1:20201/",
  );
});

test("creates a desktop-sized isolated browser window", () => {
  const options = createWindowOptions();

  assert.equal(options.width, 1280);
  assert.equal(options.height, 820);
  assert.equal(options.minWidth, 960);
  assert.equal(options.minHeight, 640);
  assert.equal(options.webPreferences.nodeIntegration, false);
  assert.equal(options.webPreferences.contextIsolation, true);
});

test("sets a window icon for desktop previews", () => {
  const options = createWindowOptions();

  assert.equal(options.icon, getWindowIconPath());
});
