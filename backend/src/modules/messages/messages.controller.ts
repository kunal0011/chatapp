import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { listConversationMessages, softDeleteMessage, searchMessages, getMessageInfo } from './messages.service.js';
import { toggleMute } from '../conversations/conversations.service.js';
import { AppError } from '../../common/errors/app-error.js';

export async function getConversationMessages(req: Request, res: Response) {
  const result = await listConversationMessages({
    conversationId: req.params.conversationId as string,
    userId: req.auth!.userId as string,
    ...(req.query.cursor && { cursor: req.query.cursor as string }),
    ...(req.query.limit && { limit: Number(req.query.limit) })
  });

  res.status(StatusCodes.OK).json(result);
}

export async function deleteMessage(req: Request, res: Response) {
  const id = req.params.id as string;
  await softDeleteMessage(id, req.auth!.userId as string);
  res.status(StatusCodes.NO_CONTENT).send();
}

export async function getMessageDetails(req: Request, res: Response) {
  const id = req.params.id as string;
  const info = await getMessageInfo(id, req.auth!.userId as string);
  res.status(StatusCodes.OK).json({ info });
}

export async function search(req: Request, res: Response) {
  const { query, conversationId } = req.query;
  if (typeof query !== 'string' || !query.trim()) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'Valid search query required');
  }

  const result = await searchMessages(req.auth!.userId as string, query.trim());

  res.status(StatusCodes.OK).json(result);
}

export async function muteConversation(req: Request, res: Response) {
  const conversationId = req.params.conversationId as string;
  await toggleMute(conversationId, req.auth!.userId as string, true);
  res.status(StatusCodes.OK).json({ message: 'Conversation muted' });
}

export async function unmuteConversation(req: Request, res: Response) {
  const conversationId = req.params.conversationId as string;
  await toggleMute(conversationId, req.auth!.userId as string, false);
  res.status(StatusCodes.OK).json({ message: 'Conversation unmuted' });
}
