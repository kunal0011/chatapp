import { z } from 'zod';

export const messageParamsSchema = z.object({
  conversationId: z.string().cuid()
});

export const messageQuerySchema = z.object({
  cursor: z.string().cuid().optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30)
});
