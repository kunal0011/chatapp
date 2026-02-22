import { Router } from 'express';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { validate } from '../../common/middleware/validate.js';
import { messageParamsSchema, messageQuerySchema } from './messages.schema.js';
import { getConversationMessages, deleteMessage, search, mute, unmute, getMessageDetails } from './messages.controller.js';

export const messagesRouter = Router();

messagesRouter.get('/search', authGuard, asyncHandler(search));
messagesRouter.get('/:id/info', authGuard, asyncHandler(getMessageDetails));

messagesRouter.get(
  '/:conversationId/history',
  authGuard,
  validate({ params: messageParamsSchema, query: messageQuerySchema }),
  asyncHandler(getConversationMessages)
);

messagesRouter.post('/conversations/:conversationId/mute', authGuard, asyncHandler(mute));
messagesRouter.post('/conversations/:conversationId/unmute', authGuard, asyncHandler(unmute));

messagesRouter.delete('/:id', authGuard, asyncHandler(deleteMessage));
