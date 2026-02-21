import { StatusCodes } from 'http-status-codes';
import { prisma } from '../../config/prisma.js';
import { AppError } from '../../common/errors/app-error.js';

export async function createOrGetDirectConversation(currentUserId: string, otherUserId: string) {
  if (currentUserId === otherUserId) {
    throw new AppError(StatusCodes.BAD_REQUEST, 'Cannot create self conversation');
  }

  const otherUser = await prisma.user.findUnique({ where: { id: otherUserId }, select: { id: true } });
  if (!otherUser) {
    throw new AppError(StatusCodes.NOT_FOUND, 'Target user not found');
  }

  const existingConversation = await prisma.conversation.findFirst({
    where: {
      type: 'DIRECT',
      members: {
        some: {
          userId: currentUserId
        }
      },
      AND: [
        {
          members: {
            some: {
              userId: otherUserId
            }
          }
        }
      ]
    },
    select: {
      id: true,
      updatedAt: true,
      members: {
        select: {
          user: {
            select: {
              id: true,
              displayName: true,
              phone: true,
              lastSeen: true
            }
          }
        }
      }
    }
  });

  if (existingConversation) {
    return existingConversation;
  }

  const conversation = await prisma.conversation.create({
    data: {
      type: 'DIRECT',
      members: {
        create: [{ userId: currentUserId }, { userId: otherUserId }]
      }
    },
    select: {
      id: true,
      updatedAt: true,
      members: {
        select: {
          user: {
            select: {
              id: true,
              displayName: true,
              phone: true,
              lastSeen: true
            }
          }
        }
      }
    }
  });

  return conversation;
}

export async function ensureConversationMembership(conversationId: string, userId: string) {
  const membership = await prisma.conversationMember.findUnique({
    where: {
      conversationId_userId: {
        conversationId,
        userId
      }
    },
    select: { id: true }
  });

  if (!membership) {
    throw new AppError(StatusCodes.FORBIDDEN, 'No access to this conversation');
  }
}

export async function toggleMute(conversationId: string, userId: string, isMuted: boolean) {
    return prisma.conversationMember.update({
        where: { conversationId_userId: { conversationId, userId } },
        data: { isMuted }
    });
}

export async function listUserConversations(userId: string) {
  return prisma.conversation.findMany({
    where: {
      members: {
        some: {
          userId
        }
      }
    },
    select: {
      id: true,
      updatedAt: true,
      members: {
        select: {
          isMuted: true,
          user: {
            select: {
              id: true,
              displayName: true,
              phone: true,
              lastSeen: true
            }
          }
        }
      },
      messages: {
        select: {
          id: true,
          content: true,
          createdAt: true,
          senderId: true
        },
        orderBy: { createdAt: 'desc' },
        take: 1
      }
    },
    orderBy: { updatedAt: 'desc' }
  });
}
