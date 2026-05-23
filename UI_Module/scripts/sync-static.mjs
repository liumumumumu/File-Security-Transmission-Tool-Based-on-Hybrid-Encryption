import { cp, rm } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const uiDir = resolve(scriptDir, "..");
const projectRoot = resolve(uiDir, "..");
const distDir = resolve(uiDir, "dist");
const staticDir = resolve(projectRoot, "TCP_Module", "src", "main", "resources", "static");

await rm(staticDir, { recursive: true, force: true });
await cp(distDir, staticDir, { recursive: true });

console.log(`Synced ${distDir} -> ${staticDir}`);

