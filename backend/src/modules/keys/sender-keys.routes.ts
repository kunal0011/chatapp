import { Router } from 'express';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import {
    distributeSenderKeys,
    getSenderKeys,
    revokeOwnSenderKey
} from './sender-keys.controller.js';

export const senderKeysRouter = Router();

// Distribute own SenderKey to group members
senderKeysRouter.post('/:groupId/sender-keys', authGuard, asyncHandler(distributeSenderKeys));

// Fetch pending SenderKeys from other group members
senderKeysRouter.get('/:groupId/sender-keys', authGuard, asyncHandler(getSenderKeys));

// Revoke own SenderKey before rotation
senderKeysRouter.delete('/:groupId/sender-keys/mine', authGuard, asyncHandler(revokeOwnSenderKey));
