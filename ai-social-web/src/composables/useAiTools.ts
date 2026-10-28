import { AI_TOOLS } from '../constants/ai-tools';
import { computed, ref, watch, onUnmounted, type Ref } from 'vue';

import { IS_DEMO_MODE, stream } from '../services/http';
import type { AiModel } from '../types/domain';
export function useAiTools(
  models: Ref<AiModel[]>,
  selected: Ref<string>,
  draft: Ref<string>,
  error: Ref<string>,
  go: (path: string) => void,
) {
  const tools = AI_TOOLS;
  const tool = ref('polish'),
    aiInput = ref(''),
    aiOutput = ref(''),
    model = ref(''),
    targetLanguage = ref('en'),
    isAiBusy = ref(false);
  const modelCapability = computed(
    () =>
      ({
        polish: 'POLISH',
        translate: 'TRANSLATION',
        'smart-reply': 'SMART_REPLY',
        summarize: 'CHAT_SUMMARY',
        schedule: 'SCHEDULE_EXTRACTION',
      })[tool.value] || 'POLISH',
  );
  const availableModels = computed(() =>
    models.value.filter(
      (m) =>
        !m.capabilities?.length ||
        m.capabilities.includes(modelCapability.value) ||
        m.capabilities.includes('text'),
    ),
  );
  watch([tool, availableModels], () => {
    if (!availableModels.value.some((m) => m.option_code === model.value))
      model.value = availableModels.value[0]?.option_code || '';
  });

  let aiController: AbortController | undefined;
  function chooseTool(id: string) {
    tool.value = id;
    aiOutput.value = '';
    go('/ai');
  }

  function returnToChat() {
    draft.value = aiOutput.value;
    go('/messages');
  }
  async function runAi() {
    if (isAiBusy.value || !aiInput.value.trim()) return;
    isAiBusy.value = true;
    aiOutput.value = '';
    error.value = '';
    aiController = new AbortController();
    try {
      if (IS_DEMO_MODE)
        aiOutput.value = `【演示结果 · 未调用 AI 服务】\n${tool.value === 'translate' ? 'Example translation: Looking forward to connecting with you.' : tool.value === 'polish' ? '示例表达：很期待与你交流这个想法，我们一起找个合适的时间聊聊吧。' : tool.value === 'smart-reply' ? '示例回复：听起来很棒！有什么需要提前准备的吗？' : '示例总结：明确沟通主题，确认参与人员，并约定下一步安排。'}`;
      else {
        if (!model.value) throw new Error('暂无可用模型，请检查后端模型配置');
        const v = aiInput.value,
          body =
            tool.value === 'polish'
              ? { message: v }
              : tool.value === 'smart-reply'
                ? {
                    message: v,
                    ...(selected.value
                      ? { conversation_public_id: selected.value }
                      : { conversation_history: [] }),
                  }
                : tool.value === 'summarize'
                  ? { content: v, summary_type: 'chat', target_length: 'short' }
                  : { text: v, target_language: targetLanguage.value };
        await stream(
          `/ai/interactions/${tool.value}`,
          { ...body, model_option_code: model.value },
          (chunk) => (aiOutput.value += chunk),
          aiController.signal,
        );
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') error.value = (e as Error).message;
    } finally {
      isAiBusy.value = false;
    }
  }
  function stopAi() {
    aiController?.abort();
  }

  onUnmounted(() => aiController?.abort());
  return {
    tools,
    tool,
    aiInput,
    aiOutput,
    model,
    targetLanguage,
    isAiBusy,
    availableModels,
    chooseTool,
    returnToChat,
    runAi,
    stopAi,
  };
}
