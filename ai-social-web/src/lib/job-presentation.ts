const JOB_TYPE_LABELS: Record<string, string> = {
  POLISH: '表达润色',
  SCHEDULE_EXTRACTION: '日程提取',
};
const JOB_STATUS_LABELS: Record<string, string> = {
  COMPLETED: '已完成',
  FAILED: '失败',
  PROCESSING: '处理中',
  PENDING: '等待中',
};
export const formatJobType = (type: string): string => JOB_TYPE_LABELS[type] || type;
export const formatJobStatus = (status: string): string => JOB_STATUS_LABELS[status] || status;
