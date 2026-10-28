import { markRaw } from 'vue';
import { WandSparkles, MessageCircle, FileText, Languages } from 'lucide-vue-next';
export const AI_TOOLS = markRaw([
  {
    id: 'polish',
    name: '表达润色',
    text: '调整措辞与语气',
    icon: WandSparkles,
    color: 'coral',
  },
  {
    id: 'smart-reply',
    name: '智能回复',
    text: '根据对话生成回复',
    icon: MessageCircle,
    color: 'purple',
  },
  {
    id: 'summarize',
    name: '内容总结',
    text: '提取内容要点',
    icon: FileText,
    color: 'green',
  },
  {
    id: 'translate',
    name: '智能翻译',
    text: '翻译为目标语言',
    icon: Languages,
    color: 'blue',
  },
]);
