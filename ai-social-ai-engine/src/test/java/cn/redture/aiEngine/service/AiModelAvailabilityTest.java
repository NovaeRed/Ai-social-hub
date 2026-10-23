package cn.redture.aiEngine.service;

import cn.redture.aiEngine.mapper.AiModelCapabilityMapper;
import cn.redture.aiEngine.pojo.entity.AiModelCapability;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.aiEngine.service.impl.AiConfigServiceImpl;
import cn.redture.aiEngine.llm.config.ModelCatalog;
import cn.redture.aiEngine.llm.config.AiChatProperties;
import cn.redture.aiEngine.llm.config.AiProviderProperties;
import cn.redture.aiEngine.llm.factory.ModelProviderStrategyFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiModelAvailabilityTest {
    @Test void onlyConfiguredModelsWithImplementedProvidersAndCredentialsAreAdvertised() {
        AiModelCapabilityMapper mapper = mock(AiModelCapabilityMapper.class);
        ModelCatalog catalog = mock(ModelCatalog.class);
        ModelProviderStrategyFactory factory = mock(ModelProviderStrategyFactory.class);
        AiConfigServiceImpl service = new AiConfigServiceImpl(null, null, mapper, null, null, null, null, null, catalog, factory);
        when(mapper.selectList(any())).thenReturn(List.of(row("ready"), row("not-configured"), row("no-provider")));
        when(factory.supportsProvider("dashscope")).thenReturn(true);
        when(catalog.findEnabledCandidateByProviderAndModel("dashscope", "ready")).thenReturn(new AiChatProperties.ModelCandidateConfig());
        AiProviderProperties.ProviderConfig config = new AiProviderProperties.ProviderConfig();
        config.setApiKey("test-only-key"); config.setUrl("https://example.test"); config.setEndpoints(Map.of("chat", "/chat"));
        when(catalog.findEnabledProviderConfig("dashscope")).thenReturn(config);
        assertEquals(List.of("ready"), service.getAvailableModels().stream().map(model -> model.getName()).toList());
        config.setApiKey("");
        assertTrue(service.getAvailableModels().isEmpty());
        config.setApiKey("test-only-key");
        when(factory.supportsProvider("dashscope")).thenReturn(false);
        assertTrue(service.getAvailableModels().isEmpty());
    }
    private AiModelCapability row(String name) {
        AiModelCapability row = new AiModelCapability(); row.setModelName(name);
        row.setProvider(name.equals("no-provider") ? "unsupported" : "dashscope");
        row.setCapabilityType(AiTaskType.POLISH); row.setIsEnabled(true); return row;
    }
}
