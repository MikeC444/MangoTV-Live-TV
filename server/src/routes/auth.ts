import { Router } from "express";
import { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { createAuthRateLimiter } from "../middleware/rateLimit.js";
import { validate } from "../middleware/validate.js";
import { loginSchema, refreshSchema, registerSchema } from "../schemas/auth.js";
import * as authService from "../services/authService.js";

const sessionIdParams = z.object({ id: z.uuid("id must be a UUID") });

function serializeTokens(tokens: authService.TokenPair) {
  return {
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt.toISOString(),
    refreshToken: tokens.refreshToken,
    refreshTokenExpiresAt: tokens.refreshTokenExpiresAt.toISOString(),
  };
}

// A factory rather than a module-level singleton router (unlike
// healthRouter/meRouter, which hold no per-instance state): this router
// mounts a rate limiter with its own in-memory counters, so building it
// fresh per createApp() call gives each app instance independent
// counters instead of sharing one across every app ever created in the
// process — the only way tests can create a fresh app per test without
// one test's auth calls silently eating into another test's rate-limit
// budget. Production only ever calls createApp() once, so this changes
// nothing there.
export function createAuthRouter(): Router {
  const authRouter = Router();
  authRouter.use(createAuthRateLimiter());

  authRouter.post("/register", validate({ body: registerSchema }), async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof registerSchema>;
      const result = await authService.register(input);
      res.status(201).json({ ...serializeTokens(result), user: result.user });
    } catch (error) {
      next(error);
    }
  });

  authRouter.post("/login", validate({ body: loginSchema }), async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof loginSchema>;
      const result = await authService.login(input);
      res.json({ ...serializeTokens(result), user: result.user });
    } catch (error) {
      next(error);
    }
  });

  authRouter.post("/refresh", validate({ body: refreshSchema }), async (req, res, next) => {
    try {
      const input = req.validated!.body as z.infer<typeof refreshSchema>;
      const tokens = await authService.refresh(input.refreshToken);
      res.json(serializeTokens(tokens));
    } catch (error) {
      next(error);
    }
  });

  // Revokes only the session that authenticated this request —
  // req.session comes from requireAuth, never from client input, so a
  // client can't ask to log out any session but its own here (that's
  // what DELETE /auth/sessions/:id below is for, with its own ownership
  // check).
  authRouter.post("/logout", requireAuth, async (req, res, next) => {
    try {
      await authService.logout(req.session!.id);
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  authRouter.get("/sessions", requireAuth, async (req, res, next) => {
    try {
      const sessions = await authService.listSessions(req.user!.id);
      res.json(
        sessions.map((session) => ({
          id: session.id,
          deviceName: session.deviceName,
          platform: session.platform,
          createdAt: session.createdAt.toISOString(),
          lastUsedAt: session.lastUsedAt.toISOString(),
          accessTokenExpiresAt: session.accessTokenExpiresAt.toISOString(),
          isCurrent: session.id === req.session!.id,
        }))
      );
    } catch (error) {
      next(error);
    }
  });

  authRouter.delete("/sessions/:id", requireAuth, validate({ params: sessionIdParams }), async (req, res, next) => {
    try {
      const { id } = req.validated!.params as z.infer<typeof sessionIdParams>;
      await authService.revokeSession(req.user!.id, id);
      res.status(204).end();
    } catch (error) {
      next(error);
    }
  });

  return authRouter;
}
