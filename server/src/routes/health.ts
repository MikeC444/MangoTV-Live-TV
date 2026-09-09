import { Router } from "express";
import { pool } from "../db/pool.js";

export const healthRouter = Router();

// Unauthenticated on purpose — this is what a deployment platform's
// health check hits, and what proves "API can connect to Neon"
// (Milestone 2's own completion criterion) without needing a session.
healthRouter.get("/", async (_req, res, next) => {
  try {
    await pool.query("SELECT 1");
    res.json({ status: "ok" });
  } catch (error) {
    next(error);
  }
});
