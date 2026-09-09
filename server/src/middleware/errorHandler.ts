import type { NextFunction, Request, Response } from "express";
import { env } from "../config/env.js";
import { HttpError } from "../lib/httpError.js";

export function notFoundHandler(_req: Request, res: Response): void {
  res.status(404).json({ error: "Not found" });
}

// Express identifies error-handling middleware specifically by its
// four-parameter signature — `next` must stay declared even though it's
// unused, or Express treats this as regular middleware and never invokes
// it on errors.
export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  const requestId = res.locals.requestId as string | undefined;

  if (err instanceof HttpError) {
    if (err.status >= 500) console.error(`[${requestId}]`, err);
    res.status(err.status).json({ error: err.message, requestId });
    return;
  }

  // Anything else is unexpected — a bug, a raw database error, a timeout
  // — and its real message never reaches the client (it can easily
  // contain SQL, connection details, or stack frames). Full detail goes
  // to the server log only; in non-production, a short `detail` field is
  // added back for local debugging convenience.
  console.error(`[${requestId}] unhandled error`, err);
  const body: { error: string; requestId?: string; detail?: string } = {
    error: "Internal server error",
    requestId,
  };
  if (env.nodeEnv !== "production" && err instanceof Error) {
    body.detail = err.message;
  }
  res.status(500).json(body);
}
