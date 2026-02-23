import { StatusCodes } from 'http-status-codes';
import { prisma } from '../../config/prisma.js';
import { AppError } from '../../common/errors/app-error.js';
import { getIO } from '../socket/socket.js';
import { deleteMemberSenderKeys } from '../keys/sender-keys.service.js';

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

import { type ConversationType } from '@prisma/client';

export async function createGroupConversation(creatorId: string, name: string, memberIds: string[], description?: string, avatarUrl?: string) {
  const conversation = await prisma.conversation.create({
    data: {
      type: 'GROUP' as ConversationType,
      name,
      ...(description && { description }),
      ...(avatarUrl && { avatarUrl }),
      creatorId,
      members: {
        create: [
          { userId: creatorId, role: 'ADMIN' as any },
          ...memberIds.map(userId => ({ userId, role: 'MEMBER' as any }))
        ]
      }
    },
    include: {
      members: {
        include: {
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

export async function addGroupMembers(conversationId: string, adminId: string, memberIds: string[]) {
  // Check if requester is admin
  const adminMembership = await prisma.conversationMember.findUnique({
    where: { conversationId_userId: { conversationId, userId: adminId } }
  });

  if (!adminMembership || adminMembership.role !== 'ADMIN') {
    throw new AppError(StatusCodes.FORBIDDEN, 'Only admins can add members');
  }

  const usersToAdd = await prisma.user.findMany({
    where: { id: { in: memberIds } },
    select: { id: true, displayName: true }
  });

  const txResult = await prisma.$transaction(async (tx) => {
    const result = await tx.conversationMember.createMany({
      data: memberIds.map(userId => ({
        conversationId,
        userId,
        role: 'MEMBER'
      })),
      skipDuplicates: true
    });

    // Create system messages for each successfully added user
    for (const user of usersToAdd) {
      await tx.message.create({
        data: {
          conversationId,
          senderId: adminId, // The admin who added them
          content: `${user.displayName} joined the group`,
          type: 'SYSTEM',
          status: 'SENT'
        }
      });
    }

    return result;
  });

  // Emit member-added events so existing members distribute SenderKeys to the new members
  const io = getIO();
  if (io) {
    for (const userId of memberIds) {
      io.to(conversationId).emit('group:member-added', { conversationId, userId });
    }
  }

  return txResult;
}

export async function removeGroupMember(conversationId: string, requesterId: string, userIdToRemove: string) {
  const requesterMembership = await prisma.conversationMember.findUnique({
    where: { conversationId_userId: { conversationId, userId: requesterId } }
  });

  if (!requesterMembership) {
    throw new AppError(StatusCodes.FORBIDDEN, 'No access to this conversation');
  }

  // Users can remove themselves, or admins can remove others
  if (requesterId !== userIdToRemove && requesterMembership.role !== 'ADMIN') {
    throw new AppError(StatusCodes.FORBIDDEN, 'Only admins can remove members');
  }

  const userToRemove = await prisma.user.findUnique({ where: { id: userIdToRemove } });

  // Use a transaction to ensure member removal and system message creation happen together
  const txResult = await prisma.$transaction(async (tx) => {
    const deleted = await tx.conversationMember.delete({
      where: { conversationId_userId: { conversationId, userId: userIdToRemove } }
    });

    if (userToRemove) {
      await tx.message.create({
        data: {
          conversationId,
          senderId: userIdToRemove, // Track who the system message is about
          content: `${userToRemove.displayName} left the group`,
          type: 'SYSTEM',
          status: 'SENT'
        }
      });
    }

    return deleted;
  });

  // Clean up SenderKey distribution blobs for the removed member
  await deleteMemberSenderKeys(conversationId, userIdToRemove);

  // Emit member-removed event so remaining members rotate their SenderKeys immediately
  const io = getIO();
  if (io) {
    io.to(conversationId).emit('group:member-removed', { conversationId, userId: userIdToRemove });
  }

  return txResult;
}

export async function updateGroupMetadata(conversationId: string, adminId: string, data: { name?: string; description?: string; avatarUrl?: string }) {
  const adminMembership = await prisma.conversationMember.findUnique({
    where: { conversationId_userId: { conversationId, userId: adminId } }
  });

  if (!adminMembership || adminMembership.role !== 'ADMIN') {
    throw new AppError(StatusCodes.FORBIDDEN, 'Only admins can update group info');
  }

  return prisma.conversation.update({
    where: { id: conversationId },
    data,
    include: {
      members: {
        include: {
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
}

export async function updateMemberRole(conversationId: string, requesterId: string, targetUserId: string, newRole: 'ADMIN' | 'MEMBER') {
  const requesterMembership = await prisma.conversationMember.findUnique({
    where: { conversationId_userId: { conversationId, userId: requesterId } }
  });

  if (!requesterMembership || requesterMembership.role !== 'ADMIN') {
    throw new AppError(StatusCodes.FORBIDDEN, 'Only admins can change roles');
  }

  const targetUser = await prisma.user.findUnique({ where: { id: targetUserId } });

  return prisma.$transaction(async (tx) => {
    const updated = await tx.conversationMember.update({
      where: { conversationId_userId: { conversationId, userId: targetUserId } },
      data: { role: newRole }
    });

    if (targetUser) {
      await tx.message.create({
        data: {
          conversationId,
          senderId: requesterId,
          content: `${targetUser.displayName} was ${newRole === 'ADMIN' ? 'promoted to Admin' : 'demoted to Member'}`,
          type: 'SYSTEM'
        }
      });
    }

    return updated;
  });
}

export async function getConversationMembers(conversationId: string, userId: string) {
  await ensureConversationMembership(conversationId, userId);

  return prisma.conversationMember.findMany({
    where: { conversationId },
    include: {
      user: {
        select: {
          id: true,
          displayName: true,
          phone: true,
          lastSeen: true
        }
      }
    },
    orderBy: { joinedAt: 'asc' }
  });
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
      type: true,
      name: true,
      avatarUrl: true,
      description: true,
      creatorId: true,
      updatedAt: true,
      members: {
        select: {
          role: true,
          isMuted: true,
          lastReadMessageId: true,
          lastReadMessage: {
            select: { createdAt: true }
          },
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
          conversationId: true,
          content: true,
          status: true,
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
    orderBy: { updatedAt: 'desc' }
  });
}
