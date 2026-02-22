import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { createOrGetDirectConversation, listUserConversations, toggleMute, createGroupConversation, addGroupMembers, removeGroupMember, updateGroupMetadata, getConversationMembers, updateMemberRole } from './conversations.service.js';

export async function createDirectConversation(req: Request, res: Response) {
  const conversation = await createOrGetDirectConversation(req.auth!.userId, req.body.otherUserId);
  res.status(StatusCodes.OK).json({ conversation });
}

export async function createGroup(req: Request, res: Response) {
  const { name, description, avatarUrl, memberIds } = req.body;
  const conversation = await createGroupConversation(req.auth!.userId, name, memberIds, description, avatarUrl);
  res.status(StatusCodes.CREATED).json({ conversation });
}

export async function getMembers(req: Request, res: Response) {
  const { id } = req.params;
  const members = await getConversationMembers(id, req.auth!.userId);
  res.status(StatusCodes.OK).json({ members });
}

export async function addMembers(req: Request, res: Response) {
  const { id } = req.params;
  const { memberIds } = req.body;
  await addGroupMembers(id, req.auth!.userId, memberIds);
  res.status(StatusCodes.OK).json({ message: 'Members added' });
}

export async function removeMember(req: Request, res: Response) {
  const { id, userId } = req.params;
  await removeGroupMember(id, req.auth!.userId, userId);
  res.status(StatusCodes.OK).json({ message: 'Member removed' });
}

export async function changeRole(req: Request, res: Response) {
  const { id, userId } = req.params;
  const { role } = req.body;
  await updateMemberRole(id, req.auth!.userId, userId, role);
  res.status(StatusCodes.OK).json({ message: 'Role updated' });
}

export async function updateGroup(req: Request, res: Response) {
  const { id } = req.params;
  const conversation = await updateGroupMetadata(id, req.auth!.userId, req.body);
  res.status(StatusCodes.OK).json({ conversation });
}

export async function getUserConversations(req: Request, res: Response) {
  const conversations = await listUserConversations(req.auth!.userId);
  res.status(StatusCodes.OK).json({ conversations });
}
