import type { Server as HttpServer } from 'node:http';
import { StatusCodes } from 'http-status-codes';
import jwt from 'jsonwebtoken';
import { Server, type Socket } from 'socket.io';
import { env } from '../../config/env.js';
import { logger } from '../../config/logger.js';
import { AppError } from '../../common/errors/app-error.js';
import { ensureConversationMembership } from '../conversations/conversations.service.js';
import { persistMessage, markConversationAsRead, editMessage, softDeleteMessage, addOrUpdateReaction } from '../messages/messages.service.js';
import { updateLastSeen } from '../users/users.service.js';

interface AuthedSocket extends Socket {
  data: {
    userId: string;
  };
}

interface MessageSendPayload {
  conversationId: string;
  content: string;
  parentId?: string;
  clientTempId?: string;
}

interface MessageEditPayload {
    messageId: string;
    content: string;
}

interface MessageUnsendPayload {
    messageId: string;
}

interface MessageReactionPayload {
    messageId: string;
    emoji: string;
}

interface MessageReadPayload {
  conversationId: string;
}

interface TypingPayload {
  conversationId: string;
}

const onlineUsers = new Set<string>();

function getTokenFromSocket(socket: Socket): string {
  const authToken = socket.handshake.auth.token;
  if (typeof authToken === 'string' && authToken.length > 0) {
    return authToken;
  }

  const header = socket.handshake.headers.authorization;
  if (typeof header === 'string' && header.startsWith('Bearer ')) {
    return header.replace('Bearer ', '');
  }

  throw new AppError(StatusCodes.UNAUTHORIZED, 'Missing access token in socket handshake');
}

function authenticateSocket(socket: Socket): string {
  const token = getTokenFromSocket(socket);
  const payload = jwt.verify(token, env.JWT_ACCESS_SECRET) as { sub: string; type: string };
  if (!payload.sub || payload.type !== 'access') {
    throw new AppError(StatusCodes.UNAUTHORIZED, 'Invalid socket token payload');
  }

  return payload.sub;
}

export function registerSocketServer(httpServer: HttpServer) {
  const io = new Server(httpServer, {
    cors: {
      origin: env.corsOrigins,
      credentials: true
    }
  });

  io.use((socket, next) => {
    try {
      const userId = authenticateSocket(socket);
      (socket as AuthedSocket).data.userId = userId;
      return next();
    } catch (error) {
      return next(error as Error);
    }
  });

  io.on('connection', async (socket: Socket) => {
    const authedSocket = socket as AuthedSocket;
    const userId = authedSocket.data.userId;
    
    onlineUsers.add(userId);
    await updateLastSeen(userId);
    logger.info({ userId, socketId: socket.id }, 'Socket connected');

    socket.on('conversation:join', async (conversationId: string) => {
      try {
        await ensureConversationMembership(conversationId, userId);
        await socket.join(conversationId);
        
        await markConversationAsRead(conversationId, userId);
        
        socket.to(conversationId).emit('conversation:read', {
          conversationId,
          readerId: userId
        });

        socket.emit('conversation:joined', { conversationId });
      } catch (error) {
        socket.emit('conversation:error', {
          conversationId,
          message: (error as Error).message
        });
      }
    });

    socket.on('typing:start', (payload: TypingPayload) => {
        socket.to(payload.conversationId).emit('typing:start', {
            conversationId: payload.conversationId,
            userId: userId
        });
    });

    socket.on('typing:stop', (payload: TypingPayload) => {
        socket.to(payload.conversationId).emit('typing:stop', {
            conversationId: payload.conversationId,
            userId: userId
        });
    });

    socket.on('message:read', async (payload: MessageReadPayload) => {
        try {
            await markConversationAsRead(payload.conversationId, userId);
            socket.to(payload.conversationId).emit('conversation:read', {
                conversationId: payload.conversationId,
                readerId: userId
            });
        } catch (error) {
            logger.error(error, 'Error handling message:read');
        }
    });

    socket.on('message:send', async (payload: MessageSendPayload) => {
      try {
        const content = payload.content.trim();
        const message = await persistMessage({
          conversationId: payload.conversationId,
          senderId: userId,
          content,
          parentId: payload.parentId
        });

        io.to(payload.conversationId).emit('message:new', { message });
        socket.emit('message:ack', {
          clientTempId: payload.clientTempId,
          messageId: message.id,
          createdAt: message.createdAt,
          status: message.status
        });
      } catch (error) {
        socket.emit('message:error', {
          clientTempId: payload.clientTempId,
          message: (error as Error).message
        });
      }
    });

    socket.on('message:edit', async (payload: MessageEditPayload) => {
        try {
            const message = await editMessage(payload.messageId, userId, payload.content.trim());
            io.to(message.conversationId).emit('message:update', { message });
        } catch (error) {
            socket.emit('message:error', { message: (error as Error).message });
        }
    });

    socket.on('message:unsend', async (payload: MessageUnsendPayload) => {
        try {
            const message = await softDeleteMessage(payload.messageId, userId);
            io.to(message.conversationId).emit('message:update', { message });
        } catch (error) {
            socket.emit('message:error', { message: (error as Error).message });
        }
    });

    socket.on('message:reaction', async (payload: MessageReactionPayload) => {
        try {
            const message = await addOrUpdateReaction(payload.messageId, userId, payload.emoji);
            if (message) {
                io.to(message.conversationId).emit('message:update', { message });
            }
        } catch (error) {
            socket.emit('message:error', { message: (error as Error).message });
        }
    });

    socket.on('disconnect', async (reason) => {
      onlineUsers.delete(userId);
      await updateLastSeen(userId);
      logger.info({ userId, socketId: socket.id, reason }, 'Socket disconnected');
    });
  });

  return io;
}
