import Redis from 'ioredis';
import { env } from './env.js';
import { logger } from './logger.js';

export const redisConfig = {
    host: process.env.REDIS_HOST || 'localhost',
    port: Number(process.env.REDIS_PORT) || 6379,
    password: process.env.REDIS_PASSWORD || undefined
};

export const pubClient = new Redis(redisConfig);
export const subClient = pubClient.duplicate();

pubClient.on('error', (err) => logger.error({ err }, 'Redis Pub Client Error'));
subClient.on('error', (err) => logger.error({ err }, 'Redis Sub Client Error'));

export const redis = new Redis(redisConfig);
redis.on('error', (err) => logger.error({ err }, 'Redis Client Error'));
