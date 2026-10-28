import type { User, Conversation, Message, Schedule, Job } from '../services/http';
export interface CursorPage<T> {
  items: T[];
  next_cursor: number | null;
  has_more: boolean;
}
export interface FriendRequest {
  request_public_id: string;
  sender: User;
  receiver: User;
  status: string;
  created_at?: string;
  message?: string;
}
export interface FriendRequests {
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
}
export interface AiModel {
  option_code: string;
  display_name: string;
  capabilities: string[];
}
export interface MessageCandidate {
  public_id?: string;
  content: string;
  sender?: string;
}
export interface GroupSummary {
  public_id: string;
  name: string;
  member_count: number;
}
export interface AiJob extends Job {
  resolved_model_name?: string;
  resolved_provider?: string;
  completed_at?: string;
  output_payload?: unknown;
  token_usage?: number;
}
export interface AiProfile {
  profile_type: string;
  content: unknown;
  model_name?: string;
  updated_at?: string;
}
export interface UsageSummary {
  total_tokens: number;
  total_cost: number;
  input_tokens: number;
  output_tokens: number;
}
export interface AiUsage {
  summary: UsageSummary;
  daily_breakdown: { date: string; tokens_used: number; cost: number }[];
  by_provider: { provider: string; tokens_used: number; cost: number }[];
}
export interface ExtractedSchedule {
  local_id: string;
  title: string;
  time: string;
  location?: string;
  start_time: string;
  end_time: string;
  isSaved: boolean;
}
export interface UploadedFile {
  public_id: string;
  original_filename: string;
  size_bytes: number;
  access_url: string;
}
export type WorkspaceData =
  | CursorPage<Conversation>
  | User[]
  | Schedule[]
  | CursorPage<AiJob>
  | FriendRequests
  | { models: AiModel[] };
export type { User, Conversation, Message, Schedule };
