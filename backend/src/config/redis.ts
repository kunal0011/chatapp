import Redis from 'ioredis';
import { env } from './env.js';
import { logger } from './logger.js';

export const redisConfig = {
    host: env.REDIS_HOST,
    port: env.REDIS_PORT,
    password: env.REDIS_PASSWORD || undefined,
    retryStrategy: (times: number) => Math.min(times * 50, 2000)
};

export const pubClient = new Redis.default(redisConfig);
export const subClient = pubClient.duplicate();

pubClient.on('error', (err: any) => logger.error({ err }, 'Redis Pub Client Error'));
subClient.on('error', (err: any) => logger.error({ err }, 'Redis Sub Client Error'));

export const redis = new Redis.default(redisConfig);
redis.on('error', (err: any) => logger.error({ err }, 'Redis Client Error'));
