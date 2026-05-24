package ai.investigator.vector.embedding;

import ai.investigator.vector.config.QdrantConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Calls Ollama's embedding API (/api/embeddings) to produce float vectors.
 * Model is configurable — default is nomic-embed-text (768 dims).
 */
@Component
public class EmbeddingService {

    private final QdrantConfig config;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public EmbeddingService(QdrantConfig config) {
        this.config = config;
    }

    public List<Float> embed(String text) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                "model", config.getEmbeddingModel(),
                "prompt", text
            ));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.getOllamaBaseUrl() + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new EmbeddingException("Ollama returned " + resp.statusCode() + ": " + resp.body());
            }

            @SuppressWarnings("unchecked")
            var root = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            List<Number> embedding = (List<Number>) root.get("embedding");

            return embedding.stream().map(Number::floatValue).toList();
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Failed to embed text", e);
        }
    }

    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String msg) { super(msg); }
        public EmbeddingException(String msg, Throwable cause) { super(msg, cause); }
    }
}
