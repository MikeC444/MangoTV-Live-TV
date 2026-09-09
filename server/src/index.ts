import { createApp } from "./app.js";
import { env } from "./config/env.js";
import { pool } from "./db/pool.js";

const app = createApp();
const server = app.listen(env.port, () => {
  console.log(`mangotv-server listening on port ${env.port} (${env.nodeEnv})`);
});

// Deploy platforms (Render/Railway/Fly/etc.) send SIGTERM before killing a
// process on every deploy or restart — without handling it, in-flight
// requests get cut off mid-response instead of finishing normally.
// server.close() stops accepting new connections and waits for existing
// ones to finish before its callback fires, so the pool is only closed
// once nothing is using it any more.
function shutdown(signal: string): void {
  console.log(`${signal} received, shutting down`);
  server.close(() => {
    void pool.end().then(() => process.exit(0));
  });
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
