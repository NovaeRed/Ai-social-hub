import { readFile, writeFile, mkdir } from 'node:fs/promises';
import pg from 'pg';

const password = process.env.SEED_PASSWORD;
if (!password) throw new Error('请设置 SEED_PASSWORD 环境变量');
const root = new URL('../../', import.meta.url);
const yaml = await readFile(new URL('ai-social-app/src/main/resources/application.yaml', root), 'utf8');
const port = yaml.match(/port:\s*(\d+)/)[1];
const base = process.env.SEED_API_BASE || `http://127.0.0.1:${port}/api/v1`;
const jdbc = yaml.match(/url:\s*jdbc:postgresql:\/\/([^\s]+)/)[1];
const db = new pg.Client({ host: jdbc.split('/')[0].split(':')[0], port: Number(jdbc.split('/')[0].split(':')[1] || 5432), database: jdbc.split('/')[1], user: yaml.match(/username:\s*(\S+)/)[1], password: yaml.match(/password:\s*(\S+)/)[1], connectionTimeoutMillis: 10000 });
const manifestPath = new URL('../.local/sample-community.json', import.meta.url);
await mkdir(new URL('../.local/', import.meta.url), { recursive: true });
let state;
try { state = JSON.parse(await readFile(manifestPath, 'utf8')); } catch { state = { accounts: [], groups: [], conversations: {}, messages: {}, schedules: {} }; }
const save = () => writeFile(manifestPath, JSON.stringify(state, null, 2));
let operations = 0;
async function api(actor, path, method = 'GET', body, attempt = 0) {
  const headers = { 'Content-Type': 'application/json' };
  if (actor?.token) headers.Authorization = 'Bearer ' + actor.token;
  const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000) });
  const data = await response.json().catch(() => null);
  operations++;
  if((response.status===429||data?.code===429)&&attempt<5){await new Promise(resolve=>setTimeout(resolve,1500*2**attempt));return api(actor,path,method,body,attempt+1);}
  if (!response.ok || data?.code >= 400) throw new Error(`${method} ${path}: ${data?.msg || response.status}`);
  return data?.data ?? data;
}
const people = [
  { slug: 'linmu', nickname: '林沐', role: '产品负责人', note: '主测试账号；全部好友、产品群群主' },
  { slug: 'yinuo', nickname: '陈一诺', role: '视觉设计师', note: '产品群管理员；有发出的待处理申请' },
  { slug: 'zhiyuan', nickname: '许知远', role: '前端工程师', note: '周末群群主；有收到的待处理申请' },
  { slug: 'xiaoman', nickname: '苏小满', role: '内容策划', note: '读书群群主、周末群管理员；有待处理申请' },
  { slug: 'yuan', nickname: '周予安', role: '摄影爱好者', note: '普通成员；有发出的待处理申请' },
];
const actors = [];
const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' });
function date(offset, time) { const day = new Date(today + 'T12:00:00+08:00'); day.setUTCDate(day.getUTCDate() + offset); return day.toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }) + 'T' + time + ':00+08:00'; }
try {
  await db.connect();
  await db.query('SET default_transaction_read_only = on');
  for (let i = 0; i < people.length; i++) {
    const person = people[i];
    let stored = state.accounts[i];
    if (!stored) {
      let username = 'gongming_' + person.slug;
      const exists = await db.query('SELECT 1 FROM users WHERE username=$1', [username]);
      if (exists.rowCount) username += '_' + Date.now().toString(36);
      stored = { username, nickname: person.nickname, registered: false };
      state.accounts[i] = stored; await save();
    }
    if (!stored.registered) {
      const token = await api(null, '/auth/register', 'POST', { username: stored.username, password, email: stored.username + '@example.test' });
      stored.registered = true; await save();
      actors.push({ ...stored, token: token.access_token });
    } else {
      const token = await api(null, '/auth/login', 'POST', { username: stored.username, password });
      actors.push({ ...stored, token: token.access_token });
    }
    const me = await api(actors[i], '/users/me', 'PATCH', { nickname: person.nickname, ai_analysis_enabled: false });
    actors[i].public_id = me.public_id; stored.public_id = me.public_id; await save();
    console.log('账号就绪', stored.username, person.nickname);
  }
  const accepted = [[0,1],[0,2],[0,3],[0,4],[1,2],[2,3],[3,4]];
  async function friend(i, j, mode = 'accept') {
    if (mode === 'accept' && (await api(actors[i], '/friends')).some(f => f.public_id === actors[j].public_id)) return;
    const existing = await api(actors[j], '/friends/requests');
    let incoming = existing.incoming?.find(r => r.sender.public_id === actors[i].public_id && r.status === 'PENDING');
    if (!incoming && mode === 'reject' && existing.incoming?.some(r => r.sender.public_id === actors[i].public_id && r.status === 'REJECTED')) return;
    if (!incoming) {
      await api(actors[i], '/friends/requests', 'POST', { target_user_public_id: actors[j].public_id, message: `你好，我是${people[i].nickname}，一起聊聊${mode === 'accept' ? '产品与生活' : '周末的新计划'}吧！` });
      incoming = (await api(actors[j], '/friends/requests')).incoming.find(r => r.sender.public_id === actors[i].public_id && r.status === 'PENDING');
    }
    if (!incoming) throw new Error('未能找到创建的好友申请');
    if (mode === 'accept') await api(actors[j], `/friends/requests/${incoming.request_public_id}/accept`, 'POST', {});
    if (mode === 'reject') await api(actors[j], `/friends/requests/${incoming.request_public_id}`, 'DELETE');
  }
  for (const [i,j] of accepted) await friend(i,j);
  await friend(1,3,'pending'); await friend(4,2,'pending'); await friend(1,4,'reject');
  console.log('好友关系及申请就绪');
  async function message(key, actor, conversation, content) {
    if (state.messages[key]) return state.messages[key];
    await new Promise(resolve=>setTimeout(resolve,180));
    const data = await api(actor, `/conversations/${conversation}/messages`, 'POST', { content, media_type: 'TEXT', temp_id: crypto.randomUUID() });
    state.messages[key] = data.public_id; await save(); return data.public_id;
  }
  for (const [i,j] of accepted) {
    const key = `${i}-${j}`;
    let id = state.conversations[key];
    if (!id) { id = await api(actors[i], '/conversations', 'POST', { target_user_public_id: actors[j].public_id }); state.conversations[key] = id; await save(); }
    await message(`private-${key}-1`, actors[i], id, `${people[j].nickname}，今天的产品方案碰头会安排在14:00，记得带上你的想法。`);
    await message(`private-${key}-2`, actors[j], id, `收到！我是${people[j].nickname}，我会提前准备好资料，一起把方案完善。`);
    await message(`private-${key}-3`, actors[i], id, '太好了，会议结束后也聊聊周末的看展计划吧。');
  }
  const groupPlans = [
    { name: '共鸣 · 产品协作小组', owner: 0, members: [1,2,3,4], admin: 1, texts: ['今天14:00在线上会议室进行产品方案碰头会，请大家准时参加。', '新版视觉稿已经准备好了，会上一起看看配色和交互。', '我会整理前端实现要点和接口联调清单。', '我准备了一版功能介绍文案，欢迎大家提建议。', '我负责整理用户反馈，重点关注手机端的操作体验。'] },
    { name: '周末 · 灵感漫游计划', owner: 2, members: [0,1,3,4], admin: 3, texts: ['明天10:00在城市美术馆门口集合，一起看展，预计12:00结束。', '我已经把明天的时间留出来了，期待一起发现新的灵感。', '想重点看看海报与装置设计展区。', '逛完展可以去附近的书店坐坐，大家觉得怎么样？', '我带相机来，帮大家记录这次周末漫游。'] },
    { name: '慢下来 · 读书与散步', owner: 3, members: [0,4], admin: null, texts: ['今晚19:30在河边步道碰面，聊聊最近读到的好书。', '我最近在读设计心理学，有几个观点很想分享。', '散步结束可以一起喝杯热茶，让今天慢慢收尾。'] },
  ];
  const sourceMessages = [];
  for (let index=0; index<groupPlans.length; index++) {
    const plan = groupPlans[index];
    let group = state.groups[index];
    if (!group) { const created = await api(actors[plan.owner], '/groups', 'POST', { name: plan.name, member_public_ids: plan.members.map(i=>actors[i].public_id) }); group={public_id:created.public_id,name:plan.name}; state.groups[index]=group; await save(); }
    if (plan.admin !== null) {
      const admins=await api(actors[plan.owner], `/groups/${group.public_id}/members/admins`);
      if(!admins.some(m=>m.public_id===actors[plan.admin].public_id))await api(actors[plan.owner], `/groups/${group.public_id}/members/admins`, 'POST', {user_public_ids:[actors[plan.admin].public_id]});
    }
    const speakers=[plan.owner,...plan.members];
    for(let n=0;n<plan.texts.length;n++) {const id=await message(`group-${index}-${n}`,actors[speakers[n%speakers.length]],group.public_id,plan.texts[n]);if(n===0)sourceMessages[index]=id;}
  }
  for(let i=0;i<actors.length;i++) {
    const plans=[
      {title:'产品方案碰头会',description:'共鸣 · 产品协作小组的共同约定，讨论视觉、开发和内容进展。',start_time:date(0,'14:00'),end_time:date(0,'15:00'),location:'共鸣线上会议室',source:0},
      {title:'周末看展与灵感漫游',description:'周末 · 灵感漫游计划；五位朋友在美术馆集合。',start_time:date(1,'10:00'),end_time:date(1,'12:00'),location:'城市美术馆正门',source:1},
      [0,3,4].includes(i)?{title:'读书分享与河边散步',description:'慢下来 · 读书与散步群的晚间约定。',start_time:date(0,'19:30'),end_time:date(0,'20:30'),location:'河边步道入口',source:2}:{title:i===1?'整理设计灵感笔记':'整理接口联调记录',description:'个人安排：沉淀今天的收获，为明天留出空间。',start_time:date(0,'19:00'),end_time:date(0,'20:00'),location:i===1?'家中书桌':'个人工作台'},
    ];
    for(let n=0;n<plans.length;n++) {
      const key=`${i}-${n}`;if(state.schedules[key])continue;
      const {source,...payload}=plans[n];
      let source_message_id=null;
      if(source!==undefined)source_message_id=(await db.query('SELECT id FROM messages WHERE public_id=$1',[sourceMessages[source]])).rows[0]?.id||null;
      const created=await api(actors[i],'/schedules','POST',{...payload,is_ai_extracted:false,source_message_id});state.schedules[key]=created.public_id;await save();
    }
  }
  const ids=(await db.query('SELECT id,username,nickname FROM users WHERE public_id=ANY($1::text[])',[actors.map(a=>a.public_id)])).rows;
  const userIds=ids.map(a=>a.id),groupIds=state.groups.map(g=>g.public_id);
  const counts=(await db.query(`SELECT
    (SELECT count(*) FROM users WHERE id=ANY($1::bigint[])) accounts,
    (SELECT count(*) FROM friendships WHERE user_id_1=ANY($1::bigint[]) AND user_id_2=ANY($1::bigint[])) friendships,
    (SELECT count(*) FROM friendship_requests WHERE sender_id=ANY($1::bigint[]) AND receiver_id=ANY($1::bigint[]) AND status='PENDING') pending_requests,
    (SELECT count(*) FROM conversations WHERE public_id=ANY($2::text[]) AND type='GROUP') groups,
    (SELECT count(*) FROM conversation_members WHERE conversation_id IN(SELECT id FROM conversations WHERE public_id=ANY($2::text[]))) group_memberships,
    (SELECT count(*) FROM messages WHERE sender_id=ANY($1::bigint[])) messages,
    (SELECT count(*) FROM schedules WHERE user_id=ANY($1::bigint[])) schedules,
    (SELECT count(*) FROM schedules WHERE user_id=ANY($1::bigint[]) AND source_message_id IS NOT NULL) linked_schedules`,[userIds,groupIds])).rows[0];
  if(Number(counts.accounts)!==5||Number(counts.friendships)!==7||Number(counts.groups)!==3||Number(counts.schedules)!==15)throw new Error('数据核对未达到预期：'+JSON.stringify(counts));
  // Reauthenticate every account with the requested password; tokens remain in memory only.
  for(const actor of actors)await api(null,'/auth/login','POST',{username:actor.username,password});
  const report={date:today,accounts:people.map((p,i)=>({username:actors[i].username,nickname:p.nickname,role:p.role,note:p.note})),groups:groupPlans.map(p=>({name:p.name,owner:people[p.owner].nickname,admin:p.admin===null?null:people[p.admin].nickname,members:[p.owner,...p.members].map(i=>people[i].nickname)})),counts,operations};
  await mkdir(new URL('../artifacts/integration/', import.meta.url), { recursive: true });
  await writeFile(new URL('../artifacts/integration/test-community-summary.json',import.meta.url),JSON.stringify(report,null,2));
  const table=report.accounts.map(a=>`| ${a.username} | ${a.nickname} | ${a.note} |`).join('\n');
  await writeFile(new URL('../artifacts/integration/test-community-guide.md',import.meta.url),`# 共鸣测试账号与关联数据\n\n创建日期：${today}（Asia/Shanghai）。密码统一使用用户指定的测试密码，本文不保存密码。\n\n| 登录用户名 | 昵称 | 测试角色 |\n| --- | --- | --- |\n${table}\n\n已创建并核对：5 个账号、7 对好友、7 个私聊、3 个群聊、34 条消息、15 条日程；另有 2 条待处理好友申请和 1 条已拒绝申请。\n\n群空间：\n\n${report.groups.map(g=>`- ${g.name}：群主${g.owner}${g.admin?'，管理员'+g.admin:''}；成员${g.members.join('、')}。`).join('\n')}\n\n每人有今天的产品会、明天的看展安排，以及晚间个人/读书散步日程；其中 13 条日程关联群内约定消息。建议先登录林沐，再切换其他账号测试未读消息、申请处理和不同群角色的操作。\n\n数据通过真实业务 API 写入，密码按后端规则加密；数据库仅只读核对，没有修改原有账号。可复用脚本为 ai-social-web/scripts/seed-test-community.mjs，需要设置 SEED_PASSWORD，幂等状态位于被 Git 忽略的 .local/sample-community.json。\n`);
  console.log(JSON.stringify(report,null,2));
} finally { await db.end(); }
