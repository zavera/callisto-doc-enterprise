package io.callistotech.enterprise.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient wired to Groq via the OpenAI-compatible endpoint.
 *
 * Groq runs Llama 3 at ~300 tokens/second — fast enough for real-time
 * per-document summaries and batch per-file summaries without adding
 * meaningful latency to the extraction pipeline.
 *
 * Model and API key are configured in application.yml under spring.ai.openai.
 * To swap providers (Azure OpenAI, Anthropic, local Ollama) change the
 * spring.ai.openai.base-url and model — SummaryService is provider-agnostic.
 */
@Configuration
public class GroqConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
