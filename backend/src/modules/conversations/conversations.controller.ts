import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { createOrGetDirectConversation, listUserConversations, toggleMute } from './conversations.service.js';

export async function createDirectConversation(req: Request, res: Response) {
  const conversation = await createOrGetDirectConversation(req.auth!.userId, req.body.otherUserId);
  res.status(StatusCodes.OK).json({ conversation });
}

export async function getUserConversations(req: Request, res: Response) {
  const conversations = await listUserConversations(req.auth!.userId);
  res.status(StatusCodes.OK).json({ conversations });
}

export async function mute(req: Request, res: Response) {
    const { id } = req.params;
    await toggleMute(id, req.auth!.userId, true);
    res.status(StatusCodes.OK).json({ message: 'Conversation muted' });
}

export async function unmute(req: Request, res: Response) {
    const { id } = req.params;
    await toggleMute(id, req.auth!.userId, false);
    res.status(StatusCodes.OK).json({ message: 'Conversation unmuted' });
}
