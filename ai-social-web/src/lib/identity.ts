import type { User } from './api';
import { displayUserName } from './presentation';

// Older registrations received a generated nickname instead of the chosen name.
export function normalizeCurrentUser(user: User): User {
  return { ...user, nickname: displayUserName(user, '朋友') };
}
