import { Router } from "express";
import type { z } from "zod";
import { requireAuth } from "../middleware/auth.js";
import { validate } from "../middleware/validate.js";
import { addonBodySchema, addonDeleteQuerySchema } from "../schemas/addons.js";
import * as addonService from "../services/addonService.js";

export const addonsRouter = Router();

function serialize(addon: addonService.UserAddon) {
  return {
    manifestUrl: addon.manifestUrl,
    addonId: addon.addonId,
    name: addon.name,
    manifestJson: addon.manifestJson,
    enabled: addon.enabled,
    sortOrder: addon.sortOrder,
    updatedAt: addon.updatedAt.toISOString(),
    deletedAt: addon.deletedAt ? addon.deletedAt.toISOString() : null,
  };
}

addonsRouter.get("/addons", requireAuth, async (req, res, next) => {
  try {
    const addons = await addonService.listActiveAddons(req.user!.id);
    res.json({ items: addons.map(serialize) });
  } catch (error) {
    next(error);
  }
});

addonsRouter.post("/addons", requireAuth, validate({ body: addonBodySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.body as z.infer<typeof addonBodySchema>;
    const addon = await addonService.upsertAddon(req.user!.id, input);
    res.json(serialize(addon));
  } catch (error) {
    next(error);
  }
});

addonsRouter.delete("/addons", requireAuth, validate({ query: addonDeleteQuerySchema }), async (req, res, next) => {
  try {
    const input = req.validated!.query as z.infer<typeof addonDeleteQuerySchema>;
    const addon = await addonService.removeAddon(req.user!.id, input);
    if (!addon) {
      res.status(204).end();
      return;
    }
    res.json(serialize(addon));
  } catch (error) {
    next(error);
  }
});
