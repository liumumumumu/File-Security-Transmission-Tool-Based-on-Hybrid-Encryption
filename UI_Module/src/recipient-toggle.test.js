import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const appVuePath = path.resolve("src/App.vue");
const appVueSource = readFileSync(appVuePath, "utf8");

test("recipient toggle uses a custom chevron icon instead of lucide chevrons", () => {
  assert.match(appVueSource, /<RecipientToggleIcon\b/);
  assert.doesNotMatch(appVueSource, /chevron-up/);
  assert.doesNotMatch(appVueSource, /chevron-down/);
});
