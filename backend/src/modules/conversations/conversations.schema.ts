import { z } from 'zod';

export const createDirectConversationSchema = z.object({
  otherUserId: z.string().cuid()
});

export const conversationIdParamSchema = z.object({
  conversationId: z.string().cuid()
});
