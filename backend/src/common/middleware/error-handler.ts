import type { NextFunction, Request, Response } from 'express';
import { Prisma } from '@prisma/client';
import { StatusCodes } from 'http-status-codes';
import { ZodError } from 'zod';
import { logger } from '../../config/logger.js';
import { AppError } from '../errors/app-error.js';

export function notFoundHandler(_req: Request, res: Response) {
  res.status(StatusCodes.NOT_FOUND).json({ error: 'Not found' });
}

export function errorHandler(error: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (error instanceof ZodError) {
    return res.status(StatusCodes.BAD_REQUEST).json({
      error: 'Validation failed',
      details: error.flatten().fieldErrors
    });
  }

  if (error instanceof Prisma.PrismaClientKnownRequestError) {
    logger.error({ error }, 'Prisma error');
    return res.status(StatusCodes.BAD_REQUEST).json({
      error: 'Database operation failed',
      code: error.code
    });
  }

  if (error instanceof AppError) {
    return res.status(error.statusCode).json({
      error: error.message,
      details: error.details
    });
  }

  logger.error(error, 'Unhandled server error');
  return res.status(StatusCodes.INTERNAL_SERVER_ERROR).json({ 
    error: 'Internal server error',
    message: error instanceof Error ? error.message : String(error)
  });
}
