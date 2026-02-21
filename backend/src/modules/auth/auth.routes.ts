import { Router } from 'express';
import { asyncHandler } from '../../common/utils/async-handler.js';
import { validate } from '../../common/middleware/validate.js';
import { loginSchema, registerSchema } from './auth.schema.js';
import { login, logout, refresh, register, requestOtpController, verifyOtpController } from './auth.controller.js';

export const authRouter = Router();

authRouter.post('/otp/request', asyncHandler(requestOtpController));
authRouter.post('/otp/verify', asyncHandler(verifyOtpController));
authRouter.post('/register', validate({ body: registerSchema }), asyncHandler(register));
authRouter.post('/login', validate({ body: loginSchema }), asyncHandler(login));
authRouter.post('/refresh', asyncHandler(refresh));
authRouter.post('/logout', asyncHandler(logout));
