import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import jwt, { type JwtPayload } from 'jsonwebtoken';
import { env } from '../../config/env.js';
import { AppError } from '../errors/app-error.js';

interface AccessPayload extends JwtPayload {
  sub: string;
  type: 'access';
}

export function authGuard(req: Request, _res: Response, next: NextFunction) {
  const authorization = req.headers.authorization;
  if (!authorization?.startsWith('Bearer ')) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Missing bearer token');
  }

  const token = authorization.replace('Bearer ', '');

  try {
    const payload = jwt.verify(token, env.JWT_ACCESS_SECRET) as AccessPayload;
    if (!payload.sub || payload.type !== 'access') {
      throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid access token payload');
    }

    req.auth = { userId: payload.sub, payload };
    next();
  } catch {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid or expired access token');
  }
}
