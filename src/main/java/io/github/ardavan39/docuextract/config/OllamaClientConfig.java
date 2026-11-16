package io.github.ardavan39.docuextract.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaClientConfig {

    public static final String OLLAMA_REST_CLIENT = "ollamaRestClient";

    @Bean(OLLAMA_REST_CLIENT)
    public RestClient ollamaRestClient(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Ollama ollama = properties.ollama();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        // Local models can take a long time to answer, especially on the first call.
        requestFactory.setReadTimeout(ollama.timeout());

        return builder
                .baseUrl(ollama.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
