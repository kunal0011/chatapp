import { Router } from 'express';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { validate } from '../../common/middleware/validate.js';
import { createDirectConversationSchema } from './conversations.schema.js';
import { createDirectConversation, getUserConversations, mute, unmute } from './conversations.controller.js';

export const conversationsRouter = Router();

conversationsRouter.get('/', authGuard, asyncHandler(getUserConversations));
conversationsRouter.post(
  '/direct',
  authGuard,
  validate({ body: createDirectConversationSchema }),
  asyncHandler(createDirectConversation)
);

conversationsRouter.post('/:id/mute', authGuard, asyncHandler(mute));
conversationsRouter.post('/:id/unmute', authGuard, asyncHandler(unmute));
