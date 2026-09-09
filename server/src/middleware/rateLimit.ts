import rateLimit from "express-rate-limit";
import { HttpError } from "../lib/httpError.js";

/**
 * One sane default across the whole API for now. There are no auth
 * endpoints yet (Milestones 3-4) to warrant a separate, stricter limiter
 * — login/QR-completion attempts deserve a tighter per-IP limit than
 * general authenticated traffic once they exist, and should get their
 * own limiter then rather than sharing this one.
 */
export const apiRateLimiter = rateLimit({
  windowMs: 60_000,
  limit: 120,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (_req, _res, next) => {
    next(new HttpError(429, "Too many requests"));
  },
});
