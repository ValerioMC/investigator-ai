package ai.investigator.vector.repository;

import ai.investigator.vector.chunker.DocumentChunker.DocumentChunk;
import ai.investigator.vector.config.QdrantConfig;
import ai.investigator.vector.embedding.EmbeddingService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Component
public class VectorRepository {

    private final QdrantConfig config;
    private final EmbeddingService embeddings;
    private QdrantClient client;

    // nomic-embed-text produces 768-dim vectors
    private static final int VECTOR_DIM = 768;

    public VectorRepository(QdrantConfig config, EmbeddingService embeddings) {
        this.config = config;
        this.embeddings = embeddings;
    }

    @PostConstruct
    void init() {
        client = new QdrantClient(
            QdrantGrpcClient.newBuilder(config.getHost(), config.getPort(), false).build()
        );
        ensureCollection(config.getDefaultCollection());
    }

    @PreDestroy
    void close() {
        client.close();
    }

    public void ensureCollection(String collection) {
        try {
            var collections = client.listCollectionsAsync().get();
            if (collections.stream().noneMatch(c -> c.equals(collection))) {
                client.createCollectionAsync(collection,
                    Collections.VectorParams.newBuilder()
                        .setSize(VECTOR_DIM)
                        .setDistance(Collections.Distance.Cosine)
                        .build()
                ).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new VectorStoreException("Failed to initialize collection: " + collection, e);
        }
    }

    public void indexChunks(List<DocumentChunk> chunks) {
        indexChunks(chunks, config.getDefaultCollection());
    }

    public void indexChunks(List<DocumentChunk> chunks, String collection) {
        ensureCollection(collection);

        List<Points.PointStruct> points = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            List<Float> vector = embeddings.embed(chunk.text());

            var payloadBuilder = Points.PointStruct.newBuilder()
                .setId(id(UUID.nameUUIDFromBytes(chunk.id().getBytes())))
                .setVectors(vectors(vector));

            chunk.metadata().forEach((k, v) -> {
                if (v instanceof String s) {
                    payloadBuilder.putPayload(k, value(s));
                } else if (v instanceof Integer i) {
                    payloadBuilder.putPayload(k, value(i));
                } else if (v instanceof List<?> list) {
                    payloadBuilder.putPayload(k, value(String.join(",", list.stream()
                        .map(Object::toString).toList())));
                }
            });

            points.add(payloadBuilder.build());
        }

        try {
            client.upsertAsync(collection, points).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new VectorStoreException("Failed to index chunks", e);
        }
    }

    public List<SearchResult> search(String query, int topK) {
        return search(query, topK, config.getDefaultCollection());
    }

    public List<SearchResult> search(String query, int topK, String collection) {
        List<Float> queryVector = embeddings.embed(query);

        try {
            var results = client.searchAsync(
                Points.SearchPoints.newBuilder()
                    .setCollectionName(collection)
                    .addAllVector(queryVector)
                    .setLimit(topK)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder()
                        .setEnable(true)
                        .build())
                    .build()
            ).get();

            return results.stream().map(r -> {
                Map<String, String> meta = new java.util.HashMap<>();
                r.getPayload().forEach((k, v) -> meta.put(k, v.getStringValue()));
                return new SearchResult(
                    r.getId().toString(),
                    (float) r.getScore(),
                    meta.getOrDefault("source", ""),
                    meta.getOrDefault("source_type", ""),
                    meta.getOrDefault("entity_ids", "")
                );
            }).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new VectorStoreException("Search failed", e);
        }
    }

    public record SearchResult(
        String id,
        float score,
        String source,
        String sourceType,
        String entityIds
    ) {}

    public static class VectorStoreException extends RuntimeException {
        public VectorStoreException(String msg, Throwable cause) { super(msg, cause); }
    }
}
