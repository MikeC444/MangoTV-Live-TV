import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { trailerQuerySchema } from "../schemas/trailers.js";
import { findTrailer } from "../services/trailerService.js";

export const trailersRouter = Router();

trailersRouter.get("/trailer", requireAuth, validate({ query: trailerQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof trailerQuerySchema>;
    const mediaType = input.type === "MOVIE" ? "movie" : "tv";
    const result = await findTrailer(input.title, input.year, mediaType);
    res.json({ youtubeVideoId: result?.youtubeVideoId ?? null });
  } catch (error) {
    next(error);
  }
});
