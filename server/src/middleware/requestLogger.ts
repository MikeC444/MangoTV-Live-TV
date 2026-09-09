import { randomUUID } from "node:crypto";
import type { NextFunction, Request, Response } from "express";

/**
 * Structured, one-line-per-request JSON logging to stdout — no request
 * bodies, headers, or query strings (which could carry tokens or other
 * sensitive values), just enough to correlate and debug: a request id
 * (also echoed back as X-Request-Id, and included in error responses so a
 * user's bug report can be matched to a log line), method, path, status,
 * duration, and the authenticated user id when there is one.
 *
 * Registered first in the middleware chain (see app.ts) so its
 * `res.on("finish")` listener is attached before anything downstream —
 * including the rate limiter — can end the response, and every request
 * gets logged regardless of where it terminates.
 */
export function requestLogger(req: Request, res: Response, next: NextFunction): void {
  const requestId = randomUUID();
  res.locals.requestId = requestId;
  res.setHeader("X-Request-Id", requestId);
  const startedAt = process.hrtime.bigint();

  res.on("finish", () => {
    const durationMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
    console.log(
      JSON.stringify({
        requestId,
        method: req.method,
        path: req.path,
        status: res.statusCode,
        durationMs: Math.round(durationMs * 100) / 100,
        userId: req.user?.id,
      })
    );
  });

  next();
}
