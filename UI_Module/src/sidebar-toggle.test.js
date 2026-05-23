import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const appVuePath = path.resolve("src/App.vue");
const appVueSource = readFileSync(appVuePath, "utf8");

test("sidebar starts collapsed and exposes open and close controls", () => {
  assert.match(appVueSource, /sidebarOpen:\s*false/);
  assert.match(appVueSource, /class="icon-button sidebar-toggle"/);
  assert.match(appVueSource, /<SidebarOpenIcon\b/);
  assert.doesNotMatch(appVueSource, /data-lucide="menu"/);
  assert.match(appVueSource, /class="icon-button subtle sidebar-close"/);
  assert.match(appVueSource, /data-lucide="arrow-left"/);
});
