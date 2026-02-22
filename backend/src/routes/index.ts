import { Router } from 'express';
import { authRouter } from '../modules/auth/auth.routes.js';
import { usersRouter } from '../modules/users/users.routes.js';
import { conversationsRouter } from '../modules/conversations/conversations.routes.js';
import { messagesRouter } from '../modules/messages/messages.routes.js';

export const apiRouter = Router();

apiRouter.use('/auth', authRouter);
apiRouter.use('/users', usersRouter);
apiRouter.use('/conversations', conversationsRouter);
apiRouter.use('/messages', messagesRouter);
