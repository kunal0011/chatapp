import { Router } from 'express';
import { authGuard } from '../../common/middleware/auth.js';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { listContacts, sync, listDirectory, addContactById, discover, getMe, updateMe, updateFcmToken, block, unblock } from './users.controller.js';

export const usersRouter = Router();

usersRouter.get('/me', authGuard, asyncHandler(getMe));
usersRouter.patch('/me', authGuard, asyncHandler(updateMe));
usersRouter.post('/fcm-token', authGuard, asyncHandler(updateFcmToken));
usersRouter.post('/:userId/block', authGuard, asyncHandler(block));
usersRouter.delete('/:userId/unblock', authGuard, asyncHandler(unblock));
usersRouter.get('/contacts', authGuard, asyncHandler(listContacts));
usersRouter.get('/directory', authGuard, asyncHandler(listDirectory));
usersRouter.post('/add', authGuard, asyncHandler(addContactById));
usersRouter.post('/discover', authGuard, asyncHandler(discover));
usersRouter.post('/sync', authGuard, asyncHandler(sync));
