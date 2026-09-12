import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { settingsBodySchema } from "../schemas/settings.js";
import * as settingsService from "../services/settingsService.js";

export const settingsRouter = Router();

function serialize(settings: settingsService.UserSettings) {
  return {
    homeRowOrder: settings.homeRowOrder,
    hiddenRowIds: settings.hiddenRowIds,
    autoplayNextEpisode: settings.autoplayNextEpisode,
    skipIntroEnabled: settings.skipIntroEnabled,
    subtitlesEnabled: settings.subtitlesEnabled,
    defaultSubtitleLanguage: settings.defaultSubtitleLanguage,
    updatedAt: settings.updatedAt ? settings.updatedAt.toISOString() : null,
  };
}

settingsRouter.get("/settings", requireAuth, async (req, res, next) => {
  try {
    const settings = await settingsService.getUserSettings(req.user!.id);
    res.json(serialize(settings));
  } catch (error) {
    next(error);
  }
});

settingsRouter.put("/settings", requireAuth, validate({ body: settingsBodySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof settingsBodySchema>;
    const settings = await settingsService.upsertUserSettings(req.user!.id, input);
    res.json(serialize(settings));
  } catch (error) {
    next(error);
  }
});
