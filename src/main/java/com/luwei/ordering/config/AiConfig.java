package com.luwei.ordering.config;

import com.luwei.ordering.service.QueryAnalyzer;
import com.luwei.ordering.service.RecommendationAssistant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port
    )
    {
        return QdrantEmbeddingStore.builder()
                                   .host(host)
                                   .port(port)
                                   .collectionName("dish")
                                   .build();
    }

    @Bean
    public RecommendationAssistant assistant(ChatModel chatModel){
        return AiServices.create(RecommendationAssistant.class , chatModel);
    }

    @Bean
    public QueryAnalyzer analyzer(ChatModel chatModel){
        return AiServices.create(QueryAnalyzer.class , chatModel);
    }
}
