import type { JwtPayload } from 'jsonwebtoken';

declare global {
  namespace Express {
    interface Request {
      auth?: {
        userId: string;
        payload: JwtPayload;
      };
    }
  }
}

export {};
