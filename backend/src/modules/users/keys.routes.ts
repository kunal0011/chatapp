import { Router } from 'express';
import { z } from 'zod';
import { validate } from '../../common/middleware/validate.js';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { uploadKeyBundle, getKeyBundle, addOneTimePreKeys, getOneTimePreKeyCount } from './keys.service.js';
import { StatusCodes } from 'http-status-codes';

export const keysRouter = Router();

// Zod schemas
const signedPreKeySchema = z.object({
    keyId: z.number().int().positive(),
    publicKey: z.string().min(1),
    signature: z.string().min(1),
});

const oneTimePreKeySchema = z.object({
    keyId: z.number().int().nonnegative(),
    publicKey: z.string().min(1),
});

const uploadKeyBundleSchema = z.object({
    identityKey: z.string().min(1),
    signedPreKey: signedPreKeySchema,
    oneTimePreKeys: z.array(oneTimePreKeySchema).min(1).max(100),
});

const replenishKeysSchema = z.object({
    oneTimePreKeys: z.array(oneTimePreKeySchema).min(1).max(100),
});

/**
 * PUT /users/me/keys
 * Upload or replace the current user's public key bundle.
 */
keysRouter.put(
    '/me/keys',
    authGuard,
    validate({ body: uploadKeyBundleSchema }),
    asyncHandler(async (req, res) => {
        await uploadKeyBundle(req.auth!.userId, req.body);
        res.status(StatusCodes.OK).json({ message: 'Key bundle uploaded successfully' });
    })
);

/**
 * GET /users/me/keys/count
 * Return the number of remaining OPKs — used by client to trigger replenishment.
 * Must come before /:userId/keys to avoid route collision.
 */
keysRouter.get(
    '/me/keys/count',
    authGuard,
    asyncHandler(async (req, res) => {
        const count = await getOneTimePreKeyCount(req.auth!.userId);
        res.status(StatusCodes.OK).json({ oneTimePreKeyCount: count });
    })
);

/**
 * POST /users/me/keys/one-time
 * Replenish the one-time prekey pool.
 */
keysRouter.post(
    '/me/keys/one-time',
    authGuard,
    validate({ body: replenishKeysSchema }),
    asyncHandler(async (req, res) => {
        await addOneTimePreKeys(req.auth!.userId, req.body.oneTimePreKeys);
        res.status(StatusCodes.OK).json({ message: 'One-time prekeys added' });
    })
);

/**
 * GET /users/:userId/keys
 * Fetch another user's public key bundle (consumes one OPK atomically).
 */
keysRouter.get(
    '/:userId/keys',
    authGuard,
    asyncHandler(async (req, res) => {
        const bundle = await getKeyBundle(req.auth!.userId, req.params.userId ?? '');
        res.status(StatusCodes.OK).json(bundle);
    })
);
