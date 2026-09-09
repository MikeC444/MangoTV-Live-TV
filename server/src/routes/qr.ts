import { Router } from "express";
import { z } from "zod";
import { getApiBaseUrl } from "../config/env.js";
import { createAuthRateLimiter, createQrPollRateLimiter } from "../middleware/rateLimit.js";
import { validate } from "../middleware/validate.js";
import { completeQrSchema, createQrSchema, qrTokenQuerySchema } from "../schemas/qr.js";
import * as qrAuthService from "../services/qrAuthService.js";

function activationUrl(token: string): string {
  const base = getApiBaseUrl().replace(/\/+$/, "");
  return `${base}/activate?token=${encodeURIComponent(token)}`;
}

/**
 * Mounted at /auth/qr, as its own router rather than nested inside
 * createAuthRouter() — that router applies a blanket 10/min limiter to
 * everything on it, which is right for register/login but would break
 * legitimate TV polling on /status (see createQrPollRateLimiter's
 * comment). Keeping this separate lets each route here carry the rate
 * limit that actually matches its traffic pattern.
 */
export function createQrRouter(): Router {
  const router = Router();

  // Unauthenticated on purpose: the TV has no account yet at this point
  // — that's the entire reason this flow exists.
  router.post("/create", createAuthRateLimiter(), validate({ body: createQrSchema }), async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof createQrSchema>;
      const session = await qrAuthService.createQrSession(input);
      res.status(201).json({
        token: session.token,
        activationUrl: activationUrl(session.token),
        expiresAt: session.expiresAt.toISOString(),
      });
    } catch (error) {
      next(error);
    }
  });

  // Read-only peek for the activation page to check a token before
  // showing the sign-in form — never issues tokens, never mutates (see
  // resolveQrSession's own comment on why that matters).
  router.get("/resolve", validate({ query: qrTokenQuerySchema }), async (req, res, next) => {
    try {
      const { token } = req.validated!.query as z.infer<typeof qrTokenQuerySchema>;
      const status = await qrAuthService.resolveQrSession(token);
      res.json({ status });
    } catch (error) {
      next(error);
    }
  });

  // The TV's poll endpoint — see pollQrSession's comment for the
  // single-use guarantee. Returns real session tokens exactly once, the
  // moment status flips to "completed".
  router.get("/status", createQrPollRateLimiter(), validate({ query: qrTokenQuerySchema }), async (req, res, next) => {
    try {
      const { token } = req.validated!.query as z.infer<typeof qrTokenQuerySchema>;
      const result = await qrAuthService.pollQrSession(token);
      if (result.status !== "completed") {
        res.json({ status: result.status });
        return;
      }
      res.json({
        status: "completed",
        accessToken: result.accessToken,
        accessTokenExpiresAt: result.accessTokenExpiresAt.toISOString(),
        refreshToken: result.refreshToken,
        refreshTokenExpiresAt: result.refreshTokenExpiresAt.toISOString(),
        user: result.user,
      });
    } catch (error) {
      next(error);
    }
  });

  // Called by the activation page once the user submits credentials.
  // Shares the strict rate limiter with register/login — this is exactly
  // as much a credential-guessing surface as those are.
  router.post("/complete", createAuthRateLimiter(), validate({ body: completeQrSchema }), async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof completeQrSchema>;
      await qrAuthService.completeQrSession(input.token, input);
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  return router;
}
