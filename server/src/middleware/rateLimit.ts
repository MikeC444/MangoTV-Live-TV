import type { NextFunction, Request, Response } from "express";
import rateLimit, { type RateLimitRequestHandler } from "express-rate-limit";
import { HttpError } from "../lib/httpError.js";

function rateLimitHandler(_req: Request, _res: Response, next: NextFunction): void {
  next(new HttpError(429, "Too many requests"));
}

/**
 * Factories, not shared singletons: each carries its own in-memory
 * counters, and constructing them fresh inside createApp() means every
 * app instance gets independent counters. In production there's only
 * ever one createApp() call at startup so this changes nothing there;
 * it's what lets tests create a fresh app per test without one test's
 * auth calls counting against another's rate-limit budget.
 */
export function createApiRateLimiter(): RateLimitRequestHandler {
  return rateLimit({
    windowMs: 60_000,
    limit: 120,
    standardHeaders: true,
    legacyHeaders: false,
    handler: rateLimitHandler,
  });
}

/**
 * Applied to the whole /auth router on top of the general limiter above —
 * register/login are password-guessing/account-spam surfaces that
 * deserve a much tighter per-IP ceiling than general authenticated
 * traffic. 10/min is generous for a real user (even mistyping a password
 * a few times) while making brute-forcing a password over this endpoint
 * impractically slow — argon2's own hashing cost is the primary defense,
 * this is defense-in-depth on top of it.
 */
export function createAuthRateLimiter(): RateLimitRequestHandler {
  return rateLimit({
    windowMs: 60_000,
    limit: 10,
    standardHeaders: true,
    legacyHeaders: false,
    handler: rateLimitHandler,
  });
}

/**
 * GET /auth/qr/status is a legitimate polling endpoint — a waiting TV is
 * expected to call it every couple of seconds — so it needs a much more
 * generous ceiling than the credential-guessing surfaces above rather
 * than sharing their 10/min limit, which a single real TV would exhaust
 * on its own within a few seconds of normal waiting.
 */
export function createQrPollRateLimiter(): RateLimitRequestHandler {
  return rateLimit({
    windowMs: 60_000,
    limit: 40,
    standardHeaders: true,
    legacyHeaders: false,
    handler: rateLimitHandler,
  });
}
