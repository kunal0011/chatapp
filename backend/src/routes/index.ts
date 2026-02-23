import { Router } from 'express';
import { authRouter } from '../modules/auth/auth.routes.js';
import { usersRouter } from '../modules/users/users.routes.js';
import { keysRouter } from '../modules/users/keys.routes.js';
import { senderKeysRouter } from '../modules/keys/sender-keys.routes.js';
import { conversationsRouter } from '../modules/conversations/conversations.routes.js';
import { messagesRouter } from '../modules/messages/messages.routes.js';

export const apiRouter = Router();

apiRouter.use('/auth', authRouter);
apiRouter.use('/users', usersRouter);
apiRouter.use('/users', keysRouter); // E2EE key management endpoints
apiRouter.use('/groups', senderKeysRouter); // Group E2EE SenderKey distribution
apiRouter.use('/conversations', conversationsRouter);
apiRouter.use('/messages', messagesRouter);
