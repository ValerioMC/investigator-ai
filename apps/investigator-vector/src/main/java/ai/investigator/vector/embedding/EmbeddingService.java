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
 * Embeds text via the OpenAI-compatible /v1/embeddings endpoint.
 * Default backend: mlx-embeddings FastAPI server (see environment/mlx/embed_server.py)
 * serving a ModernBERT/Nomic model — 768-dim output.
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
                "input", text
            ));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.getEmbeddingBaseUrl() + "/embeddings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new EmbeddingException("Embedding server returned " + resp.statusCode() + ": " + resp.body());
            }

            @SuppressWarnings("unchecked")
            var root = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            var data = (List<Map<String, Object>>) root.get("data");
            if (data == null || data.isEmpty()) {
                throw new EmbeddingException("Embedding response had no data: " + resp.body());
            }
            @SuppressWarnings("unchecked")
            List<Number> embedding = (List<Number>) data.get(0).get("embedding");

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
