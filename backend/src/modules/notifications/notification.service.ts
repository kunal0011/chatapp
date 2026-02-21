import admin from 'firebase-admin';
import { prisma } from '../../config/prisma.js';
import { logger } from '../../config/logger.js';

let isInitialized = false;

export function initFirebase() {
  try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
      const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      isInitialized = true;
      logger.info('Firebase Admin initialized');
    } else {
      logger.warn('FIREBASE_SERVICE_ACCOUNT not found, push notifications will be skipped');
    }
  } catch (error) {
    logger.error(error, 'Failed to initialize Firebase Admin');
  }
}

export async function sendPushNotification(userId: string, data: { title: string; body: string; conversationId?: string; payload?: any }) {
  if (!isInitialized) return;

  // Check if conversation is muted for this user
  if (data.conversationId) {
    const membership = await prisma.conversationMember.findUnique({
      where: { conversationId_userId: { conversationId: data.conversationId, userId } },
      select: { isMuted: true }
    });
    if (membership?.isMuted) {
      logger.info({ userId, conversationId: data.conversationId }, 'Notification skipped (muted)');
      return;
    }
  }

  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: {
      pushTokens: {
        select: { token: true }
      }
    }
  });

  const tokens = user?.pushTokens || [];
  if (tokens.length === 0) return;

  const registrationTokens = tokens.map(t => t.token);

  const message = {
    notification: {
      title: data.title,
      body: data.body
    },
    data: data.payload || {},
    tokens: registrationTokens
  };

  try {
    const response = await admin.messaging().sendEachForMulticast(message);
    logger.info({ successCount: response.successCount, failureCount: response.failureCount }, 'Push notifications sent');

    if (response.failureCount > 0) {
      const tokensToRemove: string[] = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error) {
          if (resp.error.code === 'messaging/invalid-registration-token' ||
            resp.error.code === 'messaging/registration-token-not-registered') {
            const token = registrationTokens[idx];
            if (token) {
              tokensToRemove.push(token);
            }
          }
        }
      });

      if (tokensToRemove.length > 0) {
        await prisma.user.update({
          where: { id: userId },
          data: {
            pushTokens: {
              deleteMany: { token: { in: tokensToRemove } }
            }
          }
        });
      }
    }
  } catch (error) {
    logger.error(error, 'Error sending push notification');
  }
}
