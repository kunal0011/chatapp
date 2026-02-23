import { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { AppError } from '../../common/errors/app-error.js';
import {
    storeBulkDistributions,
    fetchSenderKeys,
    revokeSenderKey
} from './sender-keys.service.js';

/**
 * POST /groups/:groupId/sender-keys
 * Body: { distributions: [{ recipientUserId, encryptedKey }] }
 *
 * Distribute the caller's SenderKey to all specified group members.
 * The encryptedKey is already 1:1 E2EE encrypted by the Android client.
 */
export async function distributeSenderKeys(req: Request, res: Response) {
    const { groupId } = req.params;
    const senderUserId = req.auth!.userId;
    const { distributions } = req.body;

    if (!Array.isArray(distributions) || distributions.length === 0) {
        throw new AppError(StatusCodes.BAD_REQUEST, 'distributions array is required');
    }

    const records = distributions.map((d: { recipientUserId: string; encryptedKey: string }) => ({
        groupId: groupId as string,
        senderUserId,
        recipientUserId: d.recipientUserId,
        encryptedKey: d.encryptedKey
    }));

    await storeBulkDistributions(records);
    res.status(StatusCodes.OK).json({ message: `Distributed sender key to ${records.length} members` });
}

/**
 * GET /groups/:groupId/sender-keys
 *
 * Fetch all pending SenderKey distributions for the caller in this group.
 */
export async function getSenderKeys(req: Request, res: Response) {
    const { groupId } = req.params;
    const recipientUserId = req.auth!.userId;

    const keys = await fetchSenderKeys(recipientUserId, groupId as string);
    res.status(StatusCodes.OK).json({ keys });
}

/**
 * DELETE /groups/:groupId/sender-keys/mine
 *
 * Revoke own SenderKey (before rotation, e.g., after a member is removed).
 */
export async function revokeOwnSenderKey(req: Request, res: Response) {
    const { groupId } = req.params;
    const senderUserId = req.auth!.userId;

    await revokeSenderKey(groupId as string, senderUserId);
    res.status(StatusCodes.OK).json({ message: 'Sender key revoked' });
}
