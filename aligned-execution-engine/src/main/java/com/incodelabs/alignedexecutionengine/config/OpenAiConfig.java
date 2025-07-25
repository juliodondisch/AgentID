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
    // Julio - mcpToolCallbacks is the tool callback bean for the MCP client tools
    public ChatClient openAiChatClient(ChatClient.Builder builder, @Qualifier("mcpToolCallbacks") List<ToolCallbackProvider> externalToolProviders) {
        List<ToolCallbackProvider> toolProviders = externalToolProviders;
        for (ToolCallbackProvider provider : toolProviders) {
            var toolCallbacks = provider.getToolCallbacks();
            log.info("Provider: {} with {} tools", provider.getClass().getSimpleName(), toolCallbacks.length);
            for (var tool : toolCallbacks) {
                log.info("  - OpenAI Chat Client Tool: {}", tool.toString());
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
