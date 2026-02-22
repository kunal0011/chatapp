import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { createOrGetDirectConversation, listUserConversations, toggleMute, createGroupConversation, addGroupMembers, removeGroupMember, updateGroupMetadata, getConversationMembers, updateMemberRole } from './conversations.service.js';

export async function createDirectConversation(req: Request, res: Response) {
  const conversation = await createOrGetDirectConversation(req.auth!.userId as string, req.body.otherUserId);
  res.status(StatusCodes.OK).json({ conversation });
}

export async function createGroup(req: Request, res: Response) {
  const { name, description, avatarUrl, memberIds } = req.body;
  const conversation = await createGroupConversation(req.auth!.userId as string, name, memberIds, description, avatarUrl);
  res.status(StatusCodes.CREATED).json({ conversation });
}

export async function getMembers(req: Request, res: Response) {
  const id = req.params.id as string;
  const members = await getConversationMembers(id, req.auth!.userId as string);
  res.status(StatusCodes.OK).json({ members });
}

export async function addMembers(req: Request, res: Response) {
  const id = req.params.id as string;
  const { memberIds } = req.body;
  await addGroupMembers(id, req.auth!.userId as string, memberIds);
  res.status(StatusCodes.OK).json({ message: 'Members added' });
}

export async function removeMember(req: Request, res: Response) {
  const id = req.params.id as string;
  const userId = req.params.userId as string;
  await removeGroupMember(id, req.auth!.userId as string, userId);
  res.status(StatusCodes.OK).json({ message: 'Member removed' });
}

export async function changeRole(req: Request, res: Response) {
  const id = req.params.id as string;
  const userId = req.params.userId as string;
  const { role } = req.body;
  await updateMemberRole(id, req.auth!.userId as string, userId, role);
  res.status(StatusCodes.OK).json({ message: 'Role updated' });
}

export async function updateGroup(req: Request, res: Response) {
  const id = req.params.id as string;
  const conversation = await updateGroupMetadata(id, req.auth!.userId as string, req.body);
  res.status(StatusCodes.OK).json({ conversation });
}

export async function getUserConversations(req: Request, res: Response) {
  const conversations = await listUserConversations(req.auth!.userId as string);
  res.status(StatusCodes.OK).json({ conversations });
}
