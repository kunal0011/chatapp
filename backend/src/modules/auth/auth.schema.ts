import { z } from 'zod';

const phoneRegex = /^\+[1-9]\d{9,14}$/;

export const registerSchema = z.object({
  phone: z.string().regex(phoneRegex, 'Use E.164 format, e.g. +15550000001'),
  displayName: z.string().min(2).max(60),
  password: z
    .string()
    .min(8)
    .max(128)
    .regex(/[A-Z]/, 'Must contain uppercase')
    .regex(/[a-z]/, 'Must contain lowercase')
    .regex(/[0-9]/, 'Must contain digit')
});

export const loginSchema = z.object({
  phone: z.string().regex(phoneRegex),
  password: z.string().min(8).max(128)
});

export const refreshSchema = z.object({
  refreshToken: z.string().min(20)
});

export type RegisterInput = z.infer<typeof registerSchema>;
export type LoginInput = z.infer<typeof loginSchema>;
export type RefreshInput = z.infer<typeof refreshSchema>;
