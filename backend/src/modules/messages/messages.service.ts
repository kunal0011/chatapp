import { prisma } from '../../config/prisma.js';
import { sendPushNotification } from '../notifications/notification.service.js';
import { isBlocked } from '../users/users.service.js';
import { AppError } from '../../common/errors/app-error.js';
import { StatusCodes } from 'http-status-codes';
import { ensureConversationMembership } from '../conversations/conversations.service.js';

const messageSelect = {
    id: true,
    content: true,
    createdAt: true,
    status: true,
    isDeleted: true,
    isEdited: true,
    conversationId: true,
    senderId: true,
    parentId: true,
    parent: {
        select: {
            id: true,
            content: true,
            senderId: true,
            sender: { select: { displayName: true } }
        }
    },
    sender: {
      select: {
        id: true,
        displayName: true,
        phone: true
      }
    },
    reactions: {
        select: {
            userId: true,
            emoji: true
        }
    }
} as const;

export async function listConversationMessages(data: {
  conversationId: string;
  userId: string;
  cursor?: string;
  limit?: number;
}) {
  const { conversationId, userId, cursor, limit = 30 } = data;

  await ensureConversationMembership(conversationId, userId);

  const messages = await prisma.message.findMany({
    where: { conversationId },
    take: limit,
    skip: cursor ? 1 : 0,
    cursor: cursor ? { id: cursor } : undefined,
    orderBy: { createdAt: 'desc' },
    select: messageSelect
  });

  const nextCursor = messages.length === limit ? messages[messages.length - 1].id : null;

  return {
    messages: messages.reverse(),
    nextCursor
  };
}

export async function searchMessages(userId: string, query: string) {
    return prisma.message.findMany({
        where: {
            conversation: {
                members: {
                    some: { userId }
                }
            },
            content: {
                contains: query,
                mode: 'insensitive'
            },
            isDeleted: false
        },
        include: {
            sender: { select: { displayName: true } },
            reactions: true
        },
        orderBy: { createdAt: 'desc' },
        take: 50
    });
}

export async function persistMessage(data: { 
    conversationId: string; 
    senderId: string; 
    content: string;
    parentId?: string;
}) {
  const conversation = await prisma.conversation.findUnique({
      where: { id: data.conversationId },
      include: { members: true }
  });

  const otherMember = conversation?.members.find(m => m.userId !== data.senderId);
  if (otherMember && await isBlocked(data.senderId, otherMember.userId)) {
      throw new AppError(StatusCodes.FORBIDDEN, 'User is blocked');
  }

  const message = await prisma.message.create({
    data: {
      conversationId: data.conversationId,
      senderId: data.senderId,
      content: data.content,
      parentId: data.parentId,
      status: 'SENT'
    },
    select: messageSelect
  });

  await prisma.conversation.update({
    where: { id: data.conversationId },
    data: { updatedAt: new Date() }
  });

  conversation?.members.filter(m => m.userId !== data.senderId).forEach(member => {
      sendPushNotification(member.userId, {
          title: message.sender.displayName,
          body: message.content,
          conversationId: message.conversationId,
          payload: {
              conversationId: message.conversationId,
              type: 'NEW_MESSAGE'
          }
      });
  });

  return message;
}

export async function addOrUpdateReaction(messageId: string, userId: string, emoji: string) {
    const message = await prisma.message.findUnique({ 
        where: { id: messageId },
        select: { conversationId: true, senderId: true } 
    });
    if (!message) throw new AppError(StatusCodes.NOT_FOUND, 'Message not found');

    await prisma.reaction.upsert({
        where: { messageId_userId: { messageId, userId } },
        update: { emoji },
        create: { messageId, userId, emoji }
    });

    const updatedMessage = await prisma.message.findUnique({
        where: { id: messageId },
        select: messageSelect
    });

    if (updatedMessage && updatedMessage.senderId !== userId) {
        sendPushNotification(updatedMessage.senderId, {
            title: 'New Reaction',
            body: `${updatedMessage.sender.displayName} reacted ${emoji} to your message`,
            conversationId: updatedMessage.conversationId,
            payload: {
                conversationId: updatedMessage.conversationId,
                type: 'REACTION'
            }
        });
    }

    return updatedMessage;
}

export async function editMessage(messageId: String, userId: string, content: string) {
    const message = await prisma.message.findUnique({ where: { id: messageId as string } });
    if (!message) throw new AppError(StatusCodes.NOT_FOUND, 'Message not found');
    if (message.senderId !== userId) throw new AppError(StatusCodes.FORBIDDEN, 'Cannot edit someone else\'s message');
    if (message.isDeleted) throw new AppError(StatusCodes.BAD_REQUEST, 'Cannot edit a deleted message');

    return prisma.message.update({
        where: { id: messageId as string },
        data: { content, isEdited: true },
        select: messageSelect
    });
}

export async function softDeleteMessage(messageId: String, userId: string) {
    const message = await prisma.message.findUnique({ where: { id: messageId as string } });
    if (!message) throw new AppError(StatusCodes.NOT_FOUND, 'Message not found');
    if (message.senderId !== userId) throw new AppError(StatusCodes.FORBIDDEN, 'Cannot delete someone else\'s message');

    return prisma.message.update({
        where: { id: messageId as string },
        data: { isDeleted: true, content: 'This message was deleted' },
        select: messageSelect
    });
}

export async function updateMessageStatus(messageId: string, status: 'DELIVERED' | 'READ') {
  return prisma.message.update({
    where: { id: messageId },
    data: { status },
    select: {
      id: true,
      conversationId: true,
      senderId: true,
      status: true
    }
  });
}

export async function markConversationAsRead(conversationId: string, userId: string) {
  await prisma.message.updateMany({
    where: {
      conversationId,
      senderId: { not: userId },
      status: { not: 'READ' }
    },
    data: { status: 'READ' }
  });
}
