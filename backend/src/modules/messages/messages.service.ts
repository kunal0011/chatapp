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
  type: true,
  status: true,
  isDeleted: true,
  isEdited: true,
  isEncrypted: true,
  ephemeralKey: true,
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
  } as any);

  const nextCursor = messages.length === limit ? messages[messages.length - 1]?.id : null;

  return {
    messages: messages.reverse(),
    nextCursor
  };
}

export async function searchMessages(userId: string, query: string) {
  console.log(`[Search] User ${userId} searching for: "${query}"`);

  const messages = await prisma.message.findMany({
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
    take: 20
  });

  const contacts = await prisma.user.findMany({
    where: {
      id: { not: userId },
      OR: [
        { displayName: { contains: query, mode: 'insensitive' } },
        { phone: { contains: query, mode: 'insensitive' } }
      ]
    },
    select: {
      id: true,
      displayName: true,
      phone: true,
      lastSeen: true
    },
    take: 10
  });

  const groups = await prisma.conversation.findMany({
    where: {
      members: { some: { userId } },
      OR: [
        { name: { contains: query, mode: 'insensitive' } },
        {
          members: {
            some: {
              user: { displayName: { contains: query, mode: 'insensitive' } }
            }
          }
        }
      ]
    },
    include: {
      _count: { select: { members: true } },
      members: {
        include: {
          user: {
            select: {
              id: true,
              displayName: true,
              phone: true,
              lastSeen: true
            }
          },
          lastReadMessage: { select: { createdAt: true } }
        }
      },
      messages: {
        select: {
          id: true,
          content: true,
          createdAt: true,
          senderId: true,
          isEncrypted: true,
          ephemeralKey: true,
          sender: {
            select: {
              id: true,
              displayName: true,
              phone: true
            }
          }
        },
        orderBy: { createdAt: 'desc' },
        take: 1
      }
    },
    take: 10
  });

  console.log(`[Search] Step Group: Found ${groups.length} groups for "${query}". Member counts: ${groups.map(g => g._count.members).join(', ')}`);

  console.log(`[Search] Found ${messages.length} messages, ${contacts.length} contacts, and ${groups.length} groups`);
  return { messages, contacts, groups };
}

export async function persistMessage(data: {
  conversationId: string;
  senderId: string;
  content: string;
  parentId?: string;
  isEncrypted?: boolean;
  ephemeralKey?: string;
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
      parentId: data.parentId || null,
      status: 'SENT',
      isEncrypted: data.isEncrypted ?? false,
      ephemeralKey: data.ephemeralKey ?? null,
    },
    include: {
      sender: {
        select: { displayName: true }
      }
    }
  });

  await prisma.conversation.update({
    where: { id: data.conversationId },
    data: { updatedAt: new Date() }
  });

  conversation?.members.filter(m => m.userId !== data.senderId).forEach(member => {
    sendPushNotification(member.userId, {
      title: message.sender?.displayName || 'New Message',
      // Never send plaintext in push notifications — message may be encrypted ciphertext.
      // Recipient will see the actual content when they open the app and decrypt locally.
      body: message.isEncrypted ? 'Encrypted message' : message.content,
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

export async function getMessageInfo(messageId: string, userId: string) {
  const message = await prisma.message.findUnique({
    where: { id: messageId },
    select: {
      id: true,
      createdAt: true,
      senderId: true,
      conversationId: true
    }
  });

  if (!message) throw new AppError(StatusCodes.NOT_FOUND, 'Message not found');
  await ensureConversationMembership(message.conversationId, userId);

  const members = await prisma.conversationMember.findMany({
    where: { conversationId: message.conversationId },
    include: {
      user: { select: { id: true, displayName: true, phone: true } },
      lastReadMessage: { select: { createdAt: true } },
      lastDeliveredMessage: { select: { createdAt: true } }
    }
  });

  const info = members
    .filter(m => m.userId !== message.senderId)
    .map(m => {
      let status: 'READ' | 'DELIVERED' | 'SENT' = 'SENT';

      if (m.lastReadMessage && m.lastReadMessage.createdAt >= message.createdAt) {
        status = 'READ';
      } else if (m.lastDeliveredMessage && m.lastDeliveredMessage.createdAt >= message.createdAt) {
        status = 'DELIVERED';
      }

      return {
        user: m.user,
        status,
        timestamp: status === 'READ' ? m.lastReadMessage?.createdAt :
          status === 'DELIVERED' ? m.lastDeliveredMessage?.createdAt : null
      };
    });

  return info;
}

export async function markConversationAsRead(conversationId: string, userId: string) {
  const lastMessage = await prisma.message.findFirst({
    where: { conversationId },
    orderBy: { createdAt: 'desc' }
  });

  if (lastMessage) {
    await prisma.conversationMember.update({
      where: { conversationId_userId: { conversationId, userId } },
      data: { lastReadMessageId: lastMessage.id }
    });
  }

  await prisma.message.updateMany({
    where: {
      conversationId,
      senderId: { not: userId },
      status: { not: 'READ' }
    },
    data: { status: 'READ' }
  });

  return lastMessage;
}
