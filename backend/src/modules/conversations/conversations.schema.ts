import { z } from 'zod';

export const createDirectConversationSchema = z.object({
  otherUserId: z.string().cuid()
});

export const createGroupSchema = z.object({
  name: z.string().min(1).max(50),
  description: z.string().max(255).optional(),
  avatarUrl: z.string().url().optional(),
  memberIds: z.array(z.string().cuid()).min(1)
});

export const addMembersSchema = z.object({
  memberIds: z.array(z.string().cuid()).min(1)
});

export const updateGroupSchema = z.object({
  name: z.string().min(1).max(50).optional(),
  description: z.string().max(255).optional(),
  avatarUrl: z.string().url().optional()
});

export const conversationIdParamSchema = z.object({
  id: z.string().cuid()
});
