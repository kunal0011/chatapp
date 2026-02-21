import { Router } from 'express';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { validate } from '../../common/middleware/validate.js';
import { messageParamsSchema, messageQuerySchema } from './messages.schema.js';
import { getConversationMessages, deleteMessage, search } from './messages.controller.js';

export const messagesRouter = Router();

messagesRouter.get('/search', authGuard, asyncHandler(search));

messagesRouter.get(
  '/:conversationId/messages',
  authGuard,
  validate({ params: messageParamsSchema, query: messageQuerySchema }),
  asyncHandler(getConversationMessages)
);

messagesRouter.delete('/:id', authGuard, asyncHandler(deleteMessage));
