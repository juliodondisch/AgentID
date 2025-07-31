package com.incodelabs.alignedexecutionengine.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class OpenAiConfig {
    @Bean("openAiChatClient") // should be renamed to action planner
    public ChatClient openAiChatClient(ChatClient.Builder builder, @Qualifier("mcpToolCallbacks") ToolCallbackProvider externalToolProvider) {
        // Only use external tools for the action planner
        List<ToolCallbackProvider> toolProviders = List.of(externalToolProvider);
        
        log.info("Action Planner - Found {} ToolCallbackProvider beans", toolProviders.size());
        for (int i = 0; i < toolProviders.size(); i++) {
            ToolCallbackProvider provider = toolProviders.get(i);
            var toolCallbacks = provider.getToolCallbacks();
            log.info("Provider {}: {} with {} tools", i, provider.getClass().getSimpleName(), toolCallbacks.length);
            for (var tool : toolCallbacks) {
                try {
                    // Try multiple approaches to get tool information
                    log.info("  - Tool class: {}", tool.getClass().getName());
                    
                    // Try to get tool definition if available
                    var getToolDefinitionMethod = tool.getClass().getMethod("getToolDefinition");
                    var toolDefinition = getToolDefinitionMethod.invoke(tool);
                    log.info("    Tool definition: {}", toolDefinition);
                    
                    // Try to get name from tool definition
                    if (toolDefinition != null) {
                        var getNameMethod = toolDefinition.getClass().getMethod("getName");
                        String toolName = (String) getNameMethod.invoke(toolDefinition);
                        log.info("    Tool name: {}", toolName);
                    }
                } catch (Exception e) {
                    log.info("  - Action Planner Tool: {} (could not get details: {})", tool.toString(), e.getMessage());
                }
            }
        }
        
        return builder
                .defaultSystem("You are helpful assistant that helps users with their tasks.")
                .defaultToolCallbacks(toolProviders.toArray(new ToolCallbackProvider[0]))
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(OpenAiApi.ChatModel.GPT_4_O)
                        .temperature(0.3)
                        .build())
                .build();
    }
}
