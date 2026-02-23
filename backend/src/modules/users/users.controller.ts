import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { getContactsForUser, syncContacts, getDirectoryUsers, addUserContact, discoverUsersByPhones, getUserById, updateProfile, registerPushToken, blockUser, unblockUser, updateKnownIdentityKey, acknowledgeKeyChange } from './users.service.js';
import { AppError } from '../../common/errors/app-error.js';

export async function listContacts(req: Request, res: Response) {
  const contacts = await getContactsForUser(req.auth!.userId);
  res.status(StatusCodes.OK).json({ contacts });
}

export async function getMe(req: Request, res: Response) {
  const user = await getUserById(req.auth!.userId);
  if (!user) throw new AppError(StatusCodes.NOT_FOUND, 'User not found');
  res.status(StatusCodes.OK).json({ user });
}

export async function getUser(req: Request, res: Response) {
  const user = await getUserById(req.params.userId as string);
  if (!user) throw new AppError(StatusCodes.NOT_FOUND, 'User not found');
  res.status(StatusCodes.OK).json({ user });
}

export async function updateMe(req: Request, res: Response) {
  const { displayName } = req.body;
  if (displayName !== undefined && (typeof displayName !== 'string' || displayName.trim().length === 0)) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'Invalid display name');
  }

  const user = await updateProfile(req.auth!.userId as string, { displayName: displayName?.trim() });
  res.status(StatusCodes.OK).json({ user });
}

export async function updateFcmToken(req: Request, res: Response) {
  const { token } = req.body;
  if (typeof token !== 'string' || token.trim().length === 0) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'token is required');
  }

  await registerPushToken(req.auth!.userId as string, token);
  res.status(StatusCodes.OK).json({ message: 'Push token registered' });
}

export async function block(req: Request, res: Response) {
  const { userId } = req.params;
  await blockUser(req.auth!.userId as string, userId as string);
  res.status(StatusCodes.OK).json({ message: 'User blocked' });
}

export async function unblock(req: Request, res: Response) {
  const { userId } = req.params;
  await unblockUser(req.auth!.userId as string, userId as string);
  res.status(StatusCodes.OK).json({ message: 'User unblocked' });
}

export async function listDirectory(req: Request, res: Response) {
  const contacts = await getDirectoryUsers(req.auth!.userId);
  res.status(StatusCodes.OK).json({ contacts });
}

export async function addContactById(req: Request, res: Response) {
  const { contactId } = req.body;
  if (typeof contactId !== 'string') {
    throw new AppError(StatusCodes.BAD_REQUEST, 'contactId is required');
  }

  const contacts = await addUserContact(req.auth!.userId, contactId);
  res.status(StatusCodes.OK).json({ contacts });
}

export async function discover(req: Request, res: Response) {
  const { phones } = req.body;
  if (!Array.isArray(phones)) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'Phones must be an array of strings');
  }

  const contacts = await discoverUsersByPhones(req.auth!.userId, phones);
  res.status(StatusCodes.OK).json({ contacts });
}

export async function sync(req: Request, res: Response) {
  const { phones } = req.body;
  if (!Array.isArray(phones)) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'Phones must be an array of strings');
  }

  const syncedCount = await syncContacts(req.auth!.userId, phones);
  const contacts = await getContactsForUser(req.auth!.userId);

  res.status(StatusCodes.OK).json({
    message: `Successfully synced ${syncedCount} contacts`,
    contacts
  });
}

/**
 * POST /users/:userId/known-key
 * Body: { identityKey: string }
 * Called when the client first fetches a contact's key bundle — caches the identity key
 * so future changes can be detected and the user can be warned.
 */
export async function acknowledgeKey(req: Request, res: Response) {
  const contactId = req.params.userId as string;
  const { identityKey } = req.body;
  if (typeof identityKey !== 'string' || identityKey.trim().length === 0) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'identityKey is required');
  }
  await updateKnownIdentityKey(req.auth!.userId, contactId, identityKey);
  res.status(StatusCodes.OK).json({ message: 'Known identity key updated' });
}

/**
 * POST /users/:userId/verify-key
 * Body: { identityKey: string }
 * Called when the user explicitly verifies the Safety Number after a key change alert.
 * Stamps ikVerifiedAt to record when they last confirmed the key.
 */
export async function verifyKey(req: Request, res: Response) {
  const contactId = req.params.userId as string;
  const { identityKey } = req.body;
  if (typeof identityKey !== 'string' || identityKey.trim().length === 0) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'identityKey is required');
  }
  await acknowledgeKeyChange(req.auth!.userId, contactId, identityKey);
  res.status(StatusCodes.OK).json({ message: 'Key change acknowledged and verified' });
}
