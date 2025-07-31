package com.incodelabs.alignedexecutionengine.config;

import com.incodelabs.alignedexecutionengine.api.mcp.PromptExecutorMcpService;
import com.incodelabs.alignedexecutionengine.service.AsyncProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class McpConfig {
    
    @Bean
    @Lazy
    public PromptExecutorMcpService promptExecutorMcpService(@Lazy AsyncProcessingService asyncProcessingService) {
        return new PromptExecutorMcpService(asyncProcessingService);
    }
    
    @Bean("localMcpTools")
    public ToolCallbackProvider localMcpTools(@Lazy PromptExecutorMcpService mcp) {
        return MethodToolCallbackProvider.builder().toolObjects(mcp).build();
    }
    
    // Julio - The mcp chat client now only uses MCP tools that are not internal
    @Bean("mcpChatClient")
    public ChatClient mcpChatClient(ChatClient.Builder builder, @Qualifier("localMcpTools") ToolCallbackProvider localMcpTools) {
        
        List<ToolCallbackProvider> toolProviders = List.of(localMcpTools);
        
        log.info("Found {} ToolCallbackProvider beans", toolProviders.size());
        for (int i = 0; i < toolProviders.size(); i++) {
            ToolCallbackProvider provider = toolProviders.get(i);
            var toolCallbacks = provider.getToolCallbacks();
            log.info("Provider {}: {} with {} tools", i, provider.getClass().getSimpleName(), 
                    toolCallbacks.length);
            for (var tool : toolCallbacks) {
                log.info("  - MCP Chat Client Tool: {}", tool.toString());
            }
        }

        
        
        var chatClientBuilder = builder
                .defaultSystem("You are a helpful assistant that can execute banking operations, check balances, and handle various requests. Use available tools when appropriate.");
        
        // Add all available tool providers (only local tools)
        chatClientBuilder.defaultToolCallbacks(toolProviders.toArray(new ToolCallbackProvider[0]));
        
        return chatClientBuilder.build();
    }

    @Bean
    public ApplicationListener<ContextRefreshedEvent> logToolCallbackProviders(ApplicationContext ctx) {
        return event -> {
            String[] beanNames = ctx.getBeanNamesForType(org.springframework.ai.tool.ToolCallbackProvider.class);
            // Use log.info instead of System.out.println to avoid polluting stdout
            log.info("=== ToolCallbackProvider beans ===");
            for (String name : beanNames) {
                Object bean = ctx.getBean(name);
                log.info("Bean: {} -> {}", name, bean.getClass().getName());
            }
            log.info("==================================");
        };
    }
}
