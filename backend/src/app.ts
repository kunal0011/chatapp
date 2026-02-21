import cookieParser from 'cookie-parser';
import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { pinoHttp } from 'pino-http';
import { StatusCodes } from 'http-status-codes';
import { env } from './config/env.js';
import { logger } from './config/logger.js';
import { errorHandler, notFoundHandler } from './common/middleware/error-handler.js';
import { apiRouter } from './routes/index.js';

export function createApp() {
  const app = express();

  app.use(pinoHttp({ logger }));
  app.use(helmet());
  app.use(
    cors({
      origin: env.corsOrigins,
      credentials: true
    })
  );

  app.use(
    rateLimit({
      windowMs: 15 * 60 * 1000,
      limit: 300,
      standardHeaders: true,
      legacyHeaders: false
    })
  );

  app.use(express.json({ limit: '1mb' }));
  app.use(cookieParser());

  app.get('/health', (_req, res) => {
    res.status(StatusCodes.OK).json({
      status: 'ok',
      uptimeSeconds: process.uptime()
    });
  });

  app.use('/api/v1', apiRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
