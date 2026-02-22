import { Router } from 'express';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { validate } from '../../common/middleware/validate.js';
import { createDirectConversationSchema, createGroupSchema, addMembersSchema, updateGroupSchema } from './conversations.schema.js';
import { createDirectConversation, getUserConversations, createGroup, addMembers, removeMember, updateGroup, getMembers, changeRole } from './conversations.controller.js';

export const conversationsRouter = Router();

conversationsRouter.get('/', authGuard, asyncHandler(getUserConversations));
conversationsRouter.get('/:id/members', authGuard, asyncHandler(getMembers));
conversationsRouter.patch('/:id/members/:userId/role', authGuard, asyncHandler(changeRole));
conversationsRouter.post(
  '/direct',
  authGuard,
  validate({ body: createDirectConversationSchema }),
  asyncHandler(createDirectConversation)
);

conversationsRouter.post(
  '/group',
  authGuard,
  validate({ body: createGroupSchema }),
  asyncHandler(createGroup)
);

conversationsRouter.post(
  '/:id/members',
  authGuard,
  validate({ body: addMembersSchema }),
  asyncHandler(addMembers)
);

conversationsRouter.delete(
  '/:id/members/:userId',
  authGuard,
  asyncHandler(removeMember)
);

conversationsRouter.patch(
  '/:id',
  authGuard,
  validate({ body: updateGroupSchema }),
  asyncHandler(updateGroup)
);
