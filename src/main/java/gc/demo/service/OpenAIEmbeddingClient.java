package gc.demo.service;

import gc.demo.config.OpenAIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class OpenAIEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingClient.class);

    private final OpenAIProperties openAIProperties;
    private final RestTemplate restTemplate;

    public OpenAIEmbeddingClient(OpenAIProperties openAIProperties, RestTemplateBuilder restTemplateBuilder) {
        this.openAIProperties = openAIProperties;
        this.restTemplate = restTemplateBuilder.build();
    }

    public Optional<float[]> embed(String input) {
        if (!StringUtils.hasText(input)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(openAIProperties.getApiKey())) {
            log.debug("OpenAI API key not configured; skipping embedding generation");
            return Optional.empty();
        }

        EmbeddingRequest request = new EmbeddingRequest(openAIProperties.getModel(), input);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openAIProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                    openAIProperties.getEndpoint(),
                    HttpMethod.POST,
                    entity,
                    EmbeddingResponse.class
            );
            EmbeddingResponse body = response.getBody();
            if (body == null || body.data() == null || body.data().isEmpty()) {
                log.warn("OpenAI embedding response did not contain data");
                return Optional.empty();
            }
            List<Double> values = body.data().get(0).embedding();
            if (values == null || values.isEmpty()) {
                log.warn("OpenAI embedding result was empty");
                return Optional.empty();
            }
            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).floatValue();
            }
            return Optional.of(embedding);
        } catch (RestClientException e) {
            log.error("Failed to generate OpenAI embedding", e);
            return Optional.empty();
        }
    }

    private record EmbeddingRequest(String model, String input, String user) {
        EmbeddingRequest(String model, String input) {
            this(model, input, null);
        }
    }

    private record EmbeddingResponse(String object, List<Data> data) {
        private record Data(String object, List<Double> embedding, int index) {}
    }
}
