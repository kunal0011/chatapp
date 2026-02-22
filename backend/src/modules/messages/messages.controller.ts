import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { listConversationMessages, softDeleteMessage, searchMessages, getMessageInfo } from './messages.service.js';
import { toggleMute } from '../conversations/conversations.service.js';
import { AppError } from '../../common/errors/app-error.js';

export async function getConversationMessages(req: Request, res: Response) {
  const result = await listConversationMessages({
    conversationId: req.params.conversationId,
    userId: req.auth!.userId,
    cursor: req.query.cursor as string | undefined,
    limit: Number(req.query.limit)
  });

  res.status(StatusCodes.OK).json(result);
}

export async function deleteMessage(req: Request, res: Response) {
    const { id } = req.params;
    await softDeleteMessage(id, req.auth!.userId);
    res.status(StatusCodes.OK).json({ message: 'Message deleted' });
}

export async function getMessageDetails(req: Request, res: Response) {
  const { id } = req.params;
  const info = await getMessageInfo(id, req.auth!.userId);
  res.status(StatusCodes.OK).json({ info });
}

export async function search(req: Request, res: Response) {
    const { q } = req.query;
    if (typeof q !== 'string' || q.trim().length === 0) {
        throw new AppError(StatusCodes.BAD_REQUEST, 'Query parameter q is required');
    }

    const result = await searchMessages(req.auth!.userId, q.trim());
    res.status(StatusCodes.OK).json(result);
}

export async function mute(req: Request, res: Response) {
    const { conversationId } = req.params;
    await toggleMute(conversationId, req.auth!.userId, true);
    res.status(StatusCodes.OK).json({ message: 'Conversation muted' });
}

export async function unmute(req: Request, res: Response) {
    const { conversationId } = req.params;
    await toggleMute(conversationId, req.auth!.userId, false);
    res.status(StatusCodes.OK).json({ message: 'Conversation unmuted' });
}
