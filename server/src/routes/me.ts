import { Router } from "express";
import { requireAuth } from "../middleware/auth.js";

export const meRouter = Router();

// Reads only req.user, set by requireAuth from the verified session —
// there is deliberately no :id param or ?id= handling anywhere here.
// Accepting a client-supplied id would let a request ask for someone
// else's profile just by naming it; see tests/me.test.ts for a test that
// a spoofed ?id= query string has no effect on the response.
meRouter.get("/me", requireAuth, (req, res, next) => {
  try {
    const user = req.user!;
    res.json({ id: user.id, email: user.email, displayName: user.displayName });
  } catch (error) {
    next(error);
  }
});
