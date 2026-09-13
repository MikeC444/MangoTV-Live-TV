import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { releaseDateQuerySchema } from "../schemas/releaseDates.js";
import { findReleaseDate } from "../services/releaseDateService.js";

export const releaseDatesRouter = Router();

releaseDatesRouter.get("/release-date", requireAuth, validate({ query: releaseDateQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof releaseDateQuerySchema>;
    const result = await findReleaseDate(input.title, input.year);
    res.json({ releaseDate: result?.releaseDate ?? null });
  } catch (error) {
    next(error);
  }
});
