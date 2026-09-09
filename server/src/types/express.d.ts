export interface AuthenticatedUser {
  id: string;
  email: string;
  displayName: string | null;
}

export interface AuthenticatedSession {
  id: string;
  deviceId: string;
}

// Global augmentation so every route/middleware file sees req.user/
// req.session without importing anything — populated only by
// middleware/auth.ts's requireAuth, and only present once it has run.
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      user?: AuthenticatedUser;
      session?: AuthenticatedSession;
    }
  }
}
