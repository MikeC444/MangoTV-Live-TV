import express, { type Express } from "express";
import helmet from "helmet";
import { errorHandler, notFoundHandler } from "./middleware/errorHandler.js";
import { createApiRateLimiter } from "./middleware/rateLimit.js";
import { requestLogger } from "./middleware/requestLogger.js";
import { createAuthRouter } from "./routes/auth.js";
import { healthRouter } from "./routes/health.js";
import { meRouter } from "./routes/me.js";

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

  app.use("/health", healthRouter);
  app.use("/auth", createAuthRouter());
  app.use("/user", meRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
