import http from 'node:http';
import { createApp } from './app.js';
import { env } from './config/env.js';
import { logger } from './config/logger.js';
import { prisma } from './config/prisma.js';
import { registerSocketServer } from './modules/socket/socket.js';
import { initFirebase } from './modules/notifications/notification.service.js';

async function bootstrap() {
  const app = createApp();
  const server = http.createServer(app);

  initFirebase();
  registerSocketServer(server);

  server.listen(env.PORT, () => {
    logger.info({ port: env.PORT }, 'Backend server started');
  });

  const shutdown = async () => {
    logger.info('Graceful shutdown initiated');
    server.close(async () => {
      await prisma.$disconnect();
      process.exit(0);
    });
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

bootstrap().catch(async (error) => {
  logger.error({ error }, 'Fatal bootstrap error');
  await prisma.$disconnect();
  process.exit(1);
});
