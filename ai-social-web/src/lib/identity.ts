import type { User } from '../services/http';
import { displayUserName } from './presentation';

// 历史注册流程曾使用系统生成的昵称，而没有保存用户选择的名称。
export function normalizeCurrentUser(user: User): User {
  return { ...user, nickname: displayUserName(user, '朋友') };
}
