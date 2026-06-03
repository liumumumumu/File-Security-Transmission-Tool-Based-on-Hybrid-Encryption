import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const appVuePath = path.resolve("src/App.vue");
const appVueSource = readFileSync(appVuePath, "utf8");

test("settings toggle uses the custom gear icon instead of the lucide settings icon", () => {
  assert.match(appVueSource, /<SettingsGearIcon\b/);
  assert.doesNotMatch(appVueSource, /data-lucide="settings-2"/);
});
