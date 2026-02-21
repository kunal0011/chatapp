import { prisma } from '../../config/prisma.js';
import { AppError } from '../../common/errors/app-error.js';
import { StatusCodes } from 'http-status-codes';

export async function getUserById(userId: string) {
  return prisma.user.findUnique({
    where: { id: userId },
    select: {
      id: true,
      phone: true,
      displayName: true,
      lastSeen: true
    }
  });
}

export async function updateProfile(userId: string, data: { displayName?: string }) {
  return prisma.user.update({
    where: { id: userId },
    data,
    select: {
      id: true,
      phone: true,
      displayName: true
    }
  });
}

export async function getContactsForUser(userId: string) {
  const contacts = await prisma.userContact.findMany({
    where: { ownerId: userId },
    include: {
      contact: {
        select: {
          id: true,
          phone: true,
          displayName: true,
          lastSeen: true
        }
      }
    }
  });

  return contacts.map((c) => c.contact);
}

export async function syncContacts(userId: string, phones: string[]) {
  const registeredUsers = await prisma.user.findMany({
    where: {
      phone: { in: phones },
      id: { not: userId }
    }
  });

  if (registeredUsers.length > 0) {
    const contactData = registeredUsers.map((u) => ({
      ownerId: userId,
      contactId: u.id
    }));

    await prisma.userContact.createMany({
      data: contactData,
      skipDuplicates: true
    });
  }

  return registeredUsers.length;
}

export async function discoverUsersByPhones(userId: string, phones: string[]) {
    const allUsers = await prisma.user.findMany({
        where: { id: { not: userId } },
        select: { id: true, phone: true, displayName: true }
    });

    const normalizedRequested = phones.map(p => p.replace(/[^0-9]/g, '').slice(-10));

    return allUsers.filter(u => {
        const normalizedUser = u.phone.replace(/[^0-9]/g, '').slice(-10);
        return normalizedRequested.includes(normalizedUser);
    });
}

export async function getDirectoryUsers(userId: string) {
    return prisma.user.findMany({
        where: { id: { not: userId } },
        select: { id: true, phone: true, displayName: true },
        take: 100
    });
}

export async function addUserContact(ownerId: string, contactId: string) {
    await prisma.userContact.upsert({
        where: { ownerId_contactId: { ownerId, contactId } },
        create: { ownerId, contactId },
        update: {}
    });
    return getContactsForUser(ownerId);
}

export async function registerPushToken(userId: string, token: string) {
    return prisma.pushToken.upsert({
        where: { token },
        update: { userId },
        create: { userId, token }
    });
}

export async function isBlocked(blockerId: string, blockedId: string): Promise<boolean> {
    const block = await prisma.blockedUser.findUnique({
        where: { blockerId_blockedId: { blockerId, blockedId } }
    });
    return !!block;
}

export async function blockUser(blockerId: string, blockedId: string) {
    return prisma.blockedUser.upsert({
        where: { blockerId_blockedId: { blockerId, blockedId } },
        create: { blockerId, blockedId },
        update: {}
    });
}

export async function unblockUser(blockerId: string, blockedId: string) {
    return prisma.blockedUser.deleteMany({
        where: { blockerId, blockedId }
    });
}

export async function updateLastSeen(userId: string) {
    try {
        return await prisma.user.updateMany({
            where: { id: userId },
            data: { lastSeen: new Date() }
        });
    } catch (error) {
        return null;
    }
}
