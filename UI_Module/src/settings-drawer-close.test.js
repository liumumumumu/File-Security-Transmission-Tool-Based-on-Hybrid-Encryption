import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const appVuePath = path.resolve("src/App.vue");
const appVueSource = readFileSync(appVuePath, "utf8");
const settingsDrawerSectionMatch = appVueSource.match(/<aside v-if="showSettingsDrawer" class="settings-drawer"[\s\S]*?<\/aside>/);
const settingsDrawerSection = settingsDrawerSectionMatch ? settingsDrawerSectionMatch[0] : "";

test("settings drawer uses a dedicated edge close tab instead of lucide x", () => {
  assert.match(settingsDrawerSection, /class="drawer-close-tab"/);
  assert.match(settingsDrawerSection, />\s*&gt;\s*</);
  assert.doesNotMatch(settingsDrawerSection, /data-lucide="x"/);
});
