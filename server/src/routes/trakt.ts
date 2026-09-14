import { Router } from "express";
import { requireAuth } from "../middleware/auth.js";
import { createTraktPollRateLimiter } from "../middleware/rateLimit.js";
import * as traktService from "../services/traktService.js";

/**
 * Account linking for Trakt (Settings > Account), backing the OAuth Device
 * Code flow -- see traktService.ts for why that flow (rather than a
 * browser-redirect Authorization Code flow) fits this app's platform.
 * Every route is scoped to req.user.id (never a client-supplied id), same
 * as every other /user/* route.
 */
export const traktRouter = Router();

traktRouter.get("/trakt", requireAuth, async (req, res, next) => {
  try {
    const status = await traktService.getConnectionStatus(req.user!.id);
    res.json(status);
  } catch (error) {
    next(error);
  }
});

traktRouter.post("/trakt/link", requireAuth, async (req, res, next) => {
  try {
    const info = await traktService.startDeviceLink(req.user!.id);
    res.status(201).json(info);
  } catch (error) {
    next(error);
  }
});

// A legitimate polling endpoint -- the TV is expected to call this every
// few seconds while the pairing screen is up -- so it needs the same kind
// of generous, poll-shaped rate limit as GET /auth/qr/status rather than
// the general API limiter's blanket ceiling.
traktRouter.get("/trakt/link", requireAuth, createTraktPollRateLimiter(), async (req, res, next) => {
  try {
    const result = await traktService.pollDeviceLink(req.user!.id);
    res.json(result);
  } catch (error) {
    next(error);
  }
});

traktRouter.delete("/trakt", requireAuth, async (req, res, next) => {
  try {
    await traktService.disconnect(req.user!.id);
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});
