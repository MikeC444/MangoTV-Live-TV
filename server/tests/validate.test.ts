import express from "express";
import request from "supertest";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import { errorHandler } from "../src/middleware/errorHandler.js";
import { validate } from "../src/middleware/validate.js";

// The validate() middleware factory has no route consumer yet in this
// milestone (see its own header comment), so it's exercised directly
// against a throwaway Express app here rather than only through a real
// route.
function buildTestApp() {
  const app = express();
  app.use(express.json());
  app.post(
    "/echo",
    validate({ body: z.object({ name: z.string().min(1) }) }),
    (req, res) => {
      res.json(req.validated!.body);
    }
  );
  app.use(errorHandler);
  return app;
}

describe("validate middleware", () => {
  it("passes valid input through as req.validated and leaves req.body untouched", async () => {
    const app = buildTestApp();
    const response = await request(app).post("/echo").send({ name: "Mango" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ name: "Mango" });
  });

  it("rejects invalid input with 400 and a descriptive error, never a stack trace", async () => {
    const app = buildTestApp();
    const response = await request(app).post("/echo").send({ name: "" });
    expect(response.status).toBe(400);
    expect(response.body.error).toContain("Invalid body");
    expect(response.body).not.toHaveProperty("stack");
  });
});
