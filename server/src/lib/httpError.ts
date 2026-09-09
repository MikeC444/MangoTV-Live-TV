/**
 * An intentional, client-facing HTTP error — thrown by routes/middleware
 * for expected failure cases (bad input, missing auth, not found) with a
 * message that's already known to be safe to show a client. The central
 * error handler (middleware/errorHandler.ts) treats anything that is
 * *not* an HttpError as unexpected and never exposes its real message to
 * the client, however it originated (a raw database error, a bug) — only
 * HttpErrors carry a message intended for the outside world.
 */
export class HttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "HttpError";
    this.status = status;
  }
}
