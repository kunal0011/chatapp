import bcrypt from 'bcryptjs';
import { StatusCodes } from 'http-status-codes';
import jwt, { type Secret, type SignOptions } from 'jsonwebtoken';
import { env } from '../../config/env.js';
import { prisma } from '../../config/prisma.js';
import { logger } from '../../config/logger.js';
import { AppError } from '../../common/errors/app-error.js';
import { sha256 } from '../../common/utils/token-hash.js';
import type { LoginInput, RegisterInput } from './auth.schema.js';
import { v4 as uuidv4 } from 'uuid';

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

interface JwtBase {
  sub: string;
  type: 'access' | 'refresh';
}

function signJwt(payload: JwtBase, secret: Secret, expiresIn: number | string): string {
  return jwt.sign(payload, secret, { expiresIn: expiresIn as any });
}

async function persistRefreshToken(userId: string, refreshToken: string, familyId: string): Promise<void> {
  const decoded = jwt.decode(refreshToken) as { exp?: number } | null;
  if (!decoded?.exp) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid refresh token payload');
  }

  await prisma.refreshToken.create({
    data: {
      userId,
      tokenHash: sha256(refreshToken),
      familyId,
      expiresAt: new Date(decoded.exp * 1000)
    }
  });
}

async function issueTokenPair(userId: string, existingFamilyId?: string): Promise<TokenPair> {
  const accessToken = signJwt(
    { sub: userId, type: 'access' },
    env.JWT_ACCESS_SECRET,
    env.JWT_ACCESS_EXPIRES_IN || '1h'
  );
  const refreshToken = signJwt(
    { sub: userId, type: 'refresh' },
    env.JWT_REFRESH_SECRET,
    env.JWT_REFRESH_EXPIRES_IN || '7d'
  );

  const familyId = existingFamilyId || uuidv4();
  await persistRefreshToken(userId, refreshToken, familyId);
  return { accessToken, refreshToken };
}

function sanitizeUser(user: { id: string; phone: string; displayName: string }) {
  return {
    id: user.id,
    phone: user.phone,
    displayName: user.displayName
  };
}

export async function requestOtp(phone: string) {
  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

  await prisma.otpVerification.upsert({
    where: { phone },
    update: { code, expiresAt },
    create: { phone, code, expiresAt }
  });

  logger.info({ phone, code }, 'OTP REQUESTED');
  return { message: 'OTP sent successfully' };
}

export async function verifyOtp(phone: string, code: string) {
  const verification = await prisma.otpVerification.findUnique({
    where: { phone }
  });

  if (!verification || verification.code !== code || verification.expiresAt < new Date()) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid or expired OTP');
  }

  const user = await prisma.user.findUnique({ where: { phone } });
  await prisma.otpVerification.delete({ where: { phone } });

  if (user) {
    const tokens = await issueTokenPair(user.id);
    return { user: sanitizeUser(user), ...tokens, isNewUser: false };
  }

  return { isNewUser: true, phone };
}

export async function registerUser(input: RegisterInput) {
  const existing = await prisma.user.findUnique({ where: { phone: input.phone } });
  if (existing) {
    throw new AppError(StatusCodes.CONFLICT, 'User with this phone already exists');
  }

  const passwordHash = await bcrypt.hash(input.password, env.BCRYPT_ROUNDS);
  const user = await prisma.user.create({
    data: {
      phone: input.phone,
      displayName: input.displayName,
      passwordHash
    },
    select: {
      id: true,
      phone: true,
      displayName: true
    }
  });

  const tokens = await issueTokenPair(user.id);
  return { user: sanitizeUser(user), ...tokens };
}

export async function loginUser(input: LoginInput) {
  const user = await prisma.user.findUnique({ where: { phone: input.phone } });
  if (!user || !user.isActive) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid credentials');
  }

  const isValid = await bcrypt.compare(input.password, user.passwordHash);
  if (!isValid) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid credentials');
  }

  const tokens = await issueTokenPair(user.id);
  return {
    user: sanitizeUser(user),
    ...tokens
  };
}

export async function refreshAccessToken(refreshToken: string) {
  let payload: { sub: string; type: string };

  try {
    payload = jwt.verify(refreshToken, env.JWT_REFRESH_SECRET) as { sub: string; type: string };
  } catch {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid or expired refresh token');
  }

  if (payload.type !== 'refresh') {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid token type');
  }

  const tokenHash = sha256(refreshToken);
  const storedToken = await prisma.refreshToken.findUnique({ where: { tokenHash } });

  if (!storedToken || storedToken.revokedAt || storedToken.expiresAt < new Date()) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Refresh token is not active');
  }

  // REUSE DETECTION
  if (storedToken.isUsed) {
    // Detected reuse! Revoke the entire family
    await prisma.refreshToken.updateMany({
      where: { familyId: storedToken.familyId || '' },
      data: { revokedAt: new Date() }
    });
    logger.warn({ userId: payload.sub, familyId: storedToken.familyId }, 'REFRESH TOKEN REUSE DETECTED');
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Security alert: Token reuse detected');
  }

  // Mark current token as used
  await prisma.refreshToken.update({
    where: { id: storedToken.id },
    data: { isUsed: true }
  });

  const user = await prisma.user.findUnique({
    where: { id: payload.sub },
    select: { id: true, phone: true, displayName: true }
  });

  if (!user) {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'User not found');
  }

  const tokens = await issueTokenPair(user.id, storedToken.familyId || undefined);
  return { user, ...tokens };
}

export async function logoutUser(refreshToken: string) {
  const tokenHash = sha256(refreshToken);
  const storedToken = await prisma.refreshToken.findUnique({ where: { tokenHash } });

  if (storedToken) {
    await prisma.refreshToken.updateMany({
      where: { familyId: storedToken.familyId },
      data: { revokedAt: new Date() }
    });
  }
}
