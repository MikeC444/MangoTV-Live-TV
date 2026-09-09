import path from "node:path";
import { fileURLToPath } from "node:url";
import express, { type Express } from "express";
import helmet from "helmet";
import { errorHandler, notFoundHandler } from "./middleware/errorHandler.js";
import { createApiRateLimiter } from "./middleware/rateLimit.js";
import { requestLogger } from "./middleware/requestLogger.js";
import { createAuthRouter } from "./routes/auth.js";
import { healthRouter } from "./routes/health.js";
import { meRouter } from "./routes/me.js";
import { createQrRouter } from "./routes/qr.js";

// Resolves correctly whether running from src/ (tsx, dev) or dist/ (built,
// prod) — public/ is a sibling of both, one level up from either.
const publicDir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "public");

/**
 * Builds the Express app without binding a port — index.ts (the real
 * process entrypoint) does that; tests import createApp() directly and
 * drive it with supertest, so a test run never opens a real socket.
 */
export function createApp(): Express {
  const app = express();

  // Deployed behind exactly one reverse proxy on every realistic target
  // (Render/Railway/Fly/etc. all put one in front) — trusting one hop is
  // what lets req.ip and the rate limiter see the real client address
  // instead of the proxy's. Revisit if a future deployment adds another
  // hop (e.g. a separate CDN) in front of that.
  app.set("trust proxy", 1);

  app.use(helmet());
  app.use(express.json({ limit: "100kb" }));
  // Registered before the rate limiter so every request gets logged
  // (including ones the limiter itself rejects), not just ones that reach
  // a route.
  app.use(requestLogger);
  app.use(createApiRateLimiter());

  // The QR activation page (Milestone 4) — served from this same backend
  // rather than a separate frontend deployment, which also means its own
  // fetch() calls to /auth/qr/* are same-origin and need no CORS
  // configuration at all. No external scripts/styles/fonts, so Helmet's
  // default CSP (script-src 'self', etc.) works unmodified.
  app.use(express.static(publicDir));
  app.get("/activate", (_req, res) => {
    res.sendFile(path.join(publicDir, "activate.html"));
  });

  app.use("/health", healthRouter);
  // Mounted before /auth: qr.ts's routes need per-route rate limits
  // tailored to polling vs. credential traffic, not the blanket limiter
  // createAuthRouter() applies to everything under /auth (see
  // routes/qr.ts's header comment).
  app.use("/auth/qr", createQrRouter());
  app.use("/auth", createAuthRouter());
  app.use("/user", meRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
