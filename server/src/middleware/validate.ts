import type { NextFunction, Request, Response } from "express";
import type { ZodType } from "zod";
import { HttpError } from "../lib/httpError.js";

interface ValidationTargets {
  body?: ZodType;
  query?: ZodType;
  params?: ZodType;
}

export interface Validated<TBody = unknown, TQuery = unknown, TParams = unknown> {
  body: TBody;
  query: TQuery;
  params: TParams;
}

declare global {
  namespace Express {
    interface Request {
      validated?: Validated;
    }
  }
}

/**
 * Generic request-validation middleware factory: parses each configured
 * target against its Zod schema and stores the result on req.validated
 * rather than overwriting req.body/query/params in place, so route
 * handlers explicitly read validated data instead of relying on an
 * in-place mutation.
 *
 * No route needs this yet — /health and /user/me take no client input.
 * Its first real caller is Milestone 3's account creation/login request
 * bodies; covered by its own unit test (tests/validate.test.ts) in the
 * meantime rather than only through a route.
 */
export function validate(targets: ValidationTargets) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    const validated: Partial<Validated> = {};
    for (const key of ["body", "query", "params"] as const) {
      const schema = targets[key];
      if (!schema) continue;
      const result = schema.safeParse(req[key]);
      if (!result.success) {
        next(new HttpError(400, `Invalid ${key}: ${result.error.issues.map((issue) => issue.message).join(", ")}`));
        return;
      }
      validated[key] = result.data;
    }
    req.validated = validated as Validated;
    next();
  };
}
