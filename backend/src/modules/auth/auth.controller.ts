import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { loginUser, logoutUser, refreshAccessToken, registerUser, requestOtp, verifyOtp } from './auth.service.js';

export async function requestOtpController(req: Request, res: Response) {
    const { phone } = req.body;
    const result = await requestOtp(phone);
    res.status(StatusCodes.OK).json(result);
}

export async function verifyOtpController(req: Request, res: Response) {
    const { phone, code } = req.body;
    const result = await verifyOtp(phone, code);
    res.status(StatusCodes.OK).json(result);
}

export async function register(req: Request, res: Response) {
  const result = await registerUser(req.body);
  res.status(StatusCodes.CREATED).json(result);
}

export async function login(req: Request, res: Response) {
  const result = await loginUser(req.body);
  res.status(StatusCodes.OK).json(result);
}

export async function refresh(req: Request, res: Response) {
  const result = await refreshAccessToken(req.body.refreshToken);
  res.status(StatusCodes.OK).json(result);
}

export async function logout(req: Request, res: Response) {
  await logoutUser(req.body.refreshToken);
  res.status(StatusCodes.NO_CONTENT).send();
}
