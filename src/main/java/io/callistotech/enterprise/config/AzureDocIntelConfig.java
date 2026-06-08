package io.callistotech.enterprise.config;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AzureDocIntelConfig.AzureDocIntelProperties.class)
public class AzureDocIntelConfig {

    @ConfigurationProperties(prefix = "azure.doc-intel")
    public record AzureDocIntelProperties(List<EndpointConfig> endpoints) {
        public record EndpointConfig(String url, String key) {}
    }

    /**
     * Builds a pool of DocumentAnalysisClient beans — one per configured endpoint.
     * AzureDocIntelExtractor round-robins across this pool for throughput.
     */
    @Bean
    public List<DocumentAnalysisClient> documentAnalysisClientPool(AzureDocIntelProperties props) {
        return props.endpoints().stream()
                .map(ep -> new DocumentAnalysisClientBuilder()
                        .endpoint(ep.url())
                        .credential(new AzureKeyCredential(ep.key()))
                        .buildClient())
                .toList();
    }
}
