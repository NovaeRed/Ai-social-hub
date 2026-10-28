import { request } from './http';
import type {
  CursorPage,
  Conversation,
  User,
  Schedule,
  AiJob,
  FriendRequests,
  AiModel,
} from '../types/domain';
type WorkspaceSnapshot = [
  PromiseSettledResult<CursorPage<Conversation>>,
  PromiseSettledResult<User[]>,
  PromiseSettledResult<Schedule[]>,
  PromiseSettledResult<CursorPage<AiJob>>,
  PromiseSettledResult<FriendRequests>,
  PromiseSettledResult<{ models: AiModel[] }>,
];
/** 并行读取各业务块；保留单项失败，避免一个接口阻塞整个工作台。 */
export async function fetchWorkspaceSnapshot(from: string, to: string): Promise<WorkspaceSnapshot> {
  return Promise.allSettled([
    request<CursorPage<Conversation>>('/conversations'),
    request<User[]>('/friends'),
    request<Schedule[]>('/schedules?' + new URLSearchParams({ start_time: from, end_time: to })),
    request<CursorPage<AiJob>>('/ai/jobs'),
    request<FriendRequests>('/friends/requests'),
    request<{ models: AiModel[] }>('/ai/models'),
  ]);
}
