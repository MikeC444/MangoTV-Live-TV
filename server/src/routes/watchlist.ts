import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { watchlistDeleteQuerySchema, watchlistItemBodySchema } from "../schemas/watchlist.js";
import * as watchlistService from "../services/watchlistService.js";

export const watchlistRouter = Router();

function serialize(item: watchlistService.WatchlistItem) {
  return {
    providerId: item.providerId,
    contentId: item.contentId,
    contentType: item.contentType,
    title: item.title,
    posterUrl: item.posterUrl,
    backdropUrl: item.backdropUrl,
    year: item.year,
    rating: item.rating,
    updatedAt: item.updatedAt.toISOString(),
    deletedAt: item.deletedAt ? item.deletedAt.toISOString() : null,
  };
}

watchlistRouter.get("/watchlist", requireAuth, async (req, res, next) => {
  try {
    const items = await watchlistService.listActiveWatchlist(req.user!.id);
    res.json({ items: items.map(serialize) });
  } catch (error) {
    next(error);
  }
});

watchlistRouter.post("/watchlist", requireAuth, validate({ body: watchlistItemBodySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof watchlistItemBodySchema>;
    const item = await watchlistService.upsertWatchlistItem(req.user!.id, input);
    res.json(serialize(item));
  } catch (error) {
    next(error);
  }
});

watchlistRouter.delete("/watchlist", requireAuth, validate({ query: watchlistDeleteQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof watchlistDeleteQuerySchema>;
    const item = await watchlistService.removeWatchlistItem(req.user!.id, input);
    if (!item) {
      // Never existed server-side for this user -- nothing to reconcile.
      res.status(204).end();
      return;
    }
    res.json(serialize(item));
  } catch (error) {
    next(error);
  }
});
