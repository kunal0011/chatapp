import { prisma } from '../../config/prisma.js';
import { AppError } from '../../common/errors/app-error.js';
import { StatusCodes } from 'http-status-codes';

export interface SignedPreKeyDto {
    keyId: number;
    publicKey: string; // base64
    signature: string; // base64
}

export interface OneTimePreKeyDto {
    keyId: number;
    publicKey: string; // base64
}

export interface KeyBundleUploadDto {
    identityKey: string; // base64 Curve25519 public key
    signedPreKey: SignedPreKeyDto;
    oneTimePreKeys: OneTimePreKeyDto[];
}

export interface KeyBundleResponseDto {
    userId: string;
    identityKey: string;
    signedPreKey: SignedPreKeyDto;
    oneTimePreKey: OneTimePreKeyDto | null; // null if exhausted (fallback to SPK only)
}

/**
 * Upload or replace a user's public key bundle.
 * Private keys NEVER touch the server — only public keys are uploaded.
 */
export async function uploadKeyBundle(userId: string, bundle: KeyBundleUploadDto): Promise<void> {
    await prisma.userKeyBundle.upsert({
        where: { userId },
        update: {
            identityKey: bundle.identityKey,
            signedPreKey: JSON.stringify(bundle.signedPreKey),
            oneTimePreKeys: JSON.stringify(bundle.oneTimePreKeys),
        },
        create: {
            userId,
            identityKey: bundle.identityKey,
            signedPreKey: JSON.stringify(bundle.signedPreKey),
            oneTimePreKeys: JSON.stringify(bundle.oneTimePreKeys),
        },
    });
}

/**
 * Fetch a recipient's public key bundle.
 * Atomically consumes one OneTimePreKey (OPK) — each OPK is used only once.
 * If OPKs are exhausted, returns null for oneTimePreKey (X3DH still works with SPK only).
 */
export async function getKeyBundle(
    requesterId: string,
    targetUserId: string
): Promise<KeyBundleResponseDto> {
    if (requesterId === targetUserId) {
        throw new AppError(StatusCodes.BAD_REQUEST, 'Cannot fetch your own key bundle this way');
    }

    const bundle = await prisma.userKeyBundle.findUnique({ where: { userId: targetUserId } });
    if (!bundle) {
        throw new AppError(
            StatusCodes.NOT_FOUND,
            `No key bundle found for user ${targetUserId}. They may not have E2EE enabled.`
        );
    }

    const allOPKs: OneTimePreKeyDto[] = JSON.parse(bundle.oneTimePreKeys);
    const signedPreKey: SignedPreKeyDto = JSON.parse(bundle.signedPreKey);

    let consumedOPK: OneTimePreKeyDto | null = null;
    if (allOPKs.length > 0) {
        // Take the first OPK and remove it atomically
        consumedOPK = allOPKs[0] || null;
        const remainingOPKs = allOPKs.slice(1);
        await prisma.userKeyBundle.update({
            where: { userId: targetUserId },
            data: { oneTimePreKeys: JSON.stringify(remainingOPKs) },
        });
    }

    return {
        userId: targetUserId,
        identityKey: bundle.identityKey,
        signedPreKey,
        oneTimePreKey: consumedOPK,
    };
}

/**
 * Replenish one-time prekeys (called when OPK count falls below a threshold).
 * New keys are appended to the existing pool.
 */
export async function addOneTimePreKeys(userId: string, newKeys: OneTimePreKeyDto[]): Promise<void> {
    const bundle = await prisma.userKeyBundle.findUnique({ where: { userId } });
    if (!bundle) {
        throw new AppError(StatusCodes.NOT_FOUND, 'Key bundle not found. Upload a full bundle first.');
    }

    const existingOPKs: OneTimePreKeyDto[] = JSON.parse(bundle.oneTimePreKeys);
    const merged = [...existingOPKs, ...newKeys];

    await prisma.userKeyBundle.update({
        where: { userId },
        data: { oneTimePreKeys: JSON.stringify(merged) },
    });
}

/**
 * Return how many OPKs remain for a user — used by the client to decide if replenishment is needed.
 */
export async function getOneTimePreKeyCount(userId: string): Promise<number> {
    const bundle = await prisma.userKeyBundle.findUnique({ where: { userId } });
    if (!bundle) return 0;
    const opks: OneTimePreKeyDto[] = JSON.parse(bundle.oneTimePreKeys);
    return opks.length;
}
