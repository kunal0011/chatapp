import { describe, expect, it } from 'vitest';
import { loginSchema, registerSchema } from '../src/modules/auth/auth.schema.js';

describe('auth schema', () => {
  it('accepts valid register payload', () => {
    const payload = {
      phone: '+15550000001',
      displayName: 'Alice',
      password: 'Password123'
    };

    const result = registerSchema.safeParse(payload);
    expect(result.success).toBe(true);
  });

  it('rejects weak password', () => {
    const payload = {
      phone: '+15550000001',
      displayName: 'Alice',
      password: 'password'
    };

    const result = registerSchema.safeParse(payload);
    expect(result.success).toBe(false);
  });

  it('requires e164 phone for login', () => {
    const result = loginSchema.safeParse({ phone: '1234', password: 'Password123' });
    expect(result.success).toBe(false);
  });
});
