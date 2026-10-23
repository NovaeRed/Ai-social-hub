-- Add only the already configured and successfully probed model.
-- Existing models, capability rows and default routes remain unchanged.
BEGIN;
SELECT pg_advisory_xact_lock(20261023, 2);
INSERT INTO ai_model_capabilities(model_name, provider, capability_type, is_enabled, is_default)
SELECT 'qwen-plus-latest', 'dashscope', capability::ai_task_type_enum, TRUE, FALSE
FROM unnest(ARRAY['POLISH','SMART_REPLY','TRANSLATION','CHAT_SUMMARY','SCHEDULE_EXTRACTION']) AS capability
WHERE NOT EXISTS (
    SELECT 1 FROM ai_model_capabilities existing
    WHERE existing.model_name='qwen-plus-latest' AND existing.provider='dashscope'
      AND existing.capability_type::text=capability
);
COMMIT;
-- Reversible availability switch, if needed (does not delete data):
-- UPDATE ai_model_capabilities SET is_enabled=FALSE, updated_at=NOW()
-- WHERE model_name='qwen-plus-latest' AND provider='dashscope';
