import type { User, Conversation, Message, Schedule, Job } from './api'
export const me: User = {
  public_id: 'demo-me',
  nickname: '林沐',
  username: 'linmu',
  email: 'linmu@example.com',
  ai_analysis_enabled: true,
}
export const friends: User[] = [
  { public_id: 'demo-1', nickname: '陈一诺', username: 'yinuo' },
  { public_id: 'demo-2', nickname: '许知远', username: 'zhiyuan' },
  { public_id: 'demo-3', nickname: '苏小满', username: 'xiaoman' },
  { public_id: 'demo-4', nickname: '周予安', username: 'yuan' },
]
export const conversations: Conversation[] = [
  {
    public_id: 'demo-conv-1',
    type: 'PRIVATE',
    name: '陈一诺',
    latest_message: { content: '周末一起去看那个新展览吧！' },
    unread_count: 2,
  },
  {
    public_id: 'demo-conv-2',
    type: 'GROUP',
    name: '灵感碰撞小分队',
    latest_message: { content: '许知远：新的设计方案已经上传啦' },
    unread_count: 5,
  },
  {
    public_id: 'demo-conv-3',
    type: 'PRIVATE',
    name: '苏小满',
    latest_message: { content: '谢谢你的推荐，这本书真的很好看' },
    unread_count: 0,
  },
  {
    public_id: 'demo-conv-4',
    type: 'PRIVATE',
    name: '周予安',
    latest_message: { content: '好呀，那我们明天下午见' },
    unread_count: 0,
  },
]
export const messages: Record<string, Message[]> = {
  'demo-conv-1': [
    {
      public_id: 'demo-msg-1',
      sender: friends[0],
      content: '最近发现了一个很棒的艺术展，感觉你会喜欢。',
      created_at: new Date().toISOString(),
    },
    {
      public_id: 'demo-msg-2',
      sender: me,
      content: '听起来很不错！在哪里呀？',
      created_at: new Date().toISOString(),
    },
    {
      public_id: 'demo-msg-3',
      sender: friends[0],
      content: '周末一起去看那个新展览吧！就在城市美术馆。',
      created_at: new Date().toISOString(),
    },
  ],
}
const day = new Date().toLocaleDateString('sv-SE')
export const schedules: Schedule[] = [
  {
    public_id: 'demo-sch-1',
    title: '产品创意碰头会',
    description: '一起聊聊新版本的灵感',
    start_time: `${day}T10:00:00+08:00`,
    end_time: `${day}T11:00:00+08:00`,
    location: '线上会议',
    is_ai_extracted: true,
  },
  {
    public_id: 'demo-sch-2',
    title: '和一诺一起看展',
    start_time: `${day}T14:30:00+08:00`,
    end_time: `${day}T16:00:00+08:00`,
    location: '城市美术馆',
    is_ai_extracted: true,
  },
  {
    public_id: 'demo-sch-3',
    title: '留一点时间给自己',
    start_time: `${day}T19:00:00+08:00`,
    end_time: `${day}T20:00:00+08:00`,
    location: '阅读 · 散步 · 放空',
  },
]
export const jobs: Job[] = [
  {
    public_id: 'demo-job-1',
    type: 'SCHEDULE_EXTRACTION',
    status: 'COMPLETED',
    created_at: new Date().toISOString(),
  },
  {
    public_id: 'demo-job-2',
    type: 'POLISH',
    status: 'COMPLETED',
    created_at: new Date().toISOString(),
  },
]
