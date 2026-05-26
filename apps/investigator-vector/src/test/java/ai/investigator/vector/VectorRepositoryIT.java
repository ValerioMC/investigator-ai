package ai.investigator.vector;

import ai.investigator.vector.chunker.DocumentChunker;
import ai.investigator.vector.config.QdrantConfig;
import ai.investigator.vector.embedding.EmbeddingService;
import ai.investigator.vector.repository.VectorRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spins up a real Qdrant container and verifies indexing + search.
 * Uses a mocked EmbeddingService that returns deterministic vectors
 * so the test doesn't require the MLX embedding server.
 */
@Testcontainers
class VectorRepositoryIT {

    @Container
    static GenericContainer<?> qdrant = new GenericContainer<>("qdrant/qdrant:v1.13.6")
        .withExposedPorts(6333, 6334)
        .waitingFor(new HttpWaitStrategy()
            .forPort(6333)
            .forPath("/readyz")
            .withStartupTimeout(Duration.ofSeconds(60)));

    static VectorRepository repo;
    static DocumentChunker chunker;

    @BeforeAll
    static void setup() {
        QdrantConfig cfg = new QdrantConfig() {
            public String host() { return qdrant.getHost(); }
            public int port() { return qdrant.getMappedPort(6333); }
            public String defaultCollection() { return "test_docs"; }
            public String embeddingModel() { return "mlx-community/nomicai-modernbert-embed-base-bf16"; }
            public String embeddingBaseUrl() { return "http://localhost:8082/v1"; }
        };

        // mock embedder — returns a deterministic 768-dim vector based on text hash
        EmbeddingService mockEmbedder = mock(EmbeddingService.class);
        when(mockEmbedder.embed(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            float base = (float) (text.hashCode() % 1000) / 1000.0f;
            List<Float> vec = new java.util.ArrayList<>(768);
            for (int i = 0; i < 768; i++) vec.add(base + i * 0.0001f);
            return vec;
        });

        repo = new VectorRepository();
        injectField(repo, "config", cfg);
        injectField(repo, "embeddings", mockEmbedder);
        repo.init();

        chunker = new DocumentChunker();
    }

    @Test
    void shouldIndexAndRetrieveDocuments() {
        String doc1 = "Marco Ferretti è risultato titolare effettivo di Costruzioni Ferretti Srl, " +
            "società che ha ottenuto numerosi appalti pubblici dal Comune di Brescia durante " +
            "il mandato dello stesso Ferretti come consigliere comunale.";
        String doc2 = "Secondo i registri camerali, LuxHold SA controlla il 51% di Costruzioni " +
            "Ferretti Srl. LuxHold è registrata in Lussemburgo e il suo titolare effettivo " +
            "risulta essere Mario Conti, fratello di Luigi Conti, sindaco di Brescia.";
        String doc3 = "Il Tribunale di Brescia ha condannato nel 2019 Roberto Esposito per " +
            "corruzione nel settore degli appalti pubblici. Esposito era legato a diverse " +
            "società offshore nelle Isole Vergini Britanniche.";
        String doc4 = "Indagini della Guardia di Finanza rivelano flussi anomali di denaro " +
            "tra conti correnti esteri e società italiane attive nel settore delle costruzioni.";
        String doc5 = "Analisi dei bilanci di Costruzioni Ferretti Srl evidenzia margini " +
            "operativi superiori alla media di settore, con dividendi straordinari distribuiti " +
            "negli anni immediatamente successivi all'aggiudicazione di appalti pubblici.";

        List<String> entities1 = List.of("p1", "c1");
        List<String> entities2 = List.of("c1", "c2");
        List<String> entities3 = List.of("p3");
        List<String> entities4 = List.of();
        List<String> entities5 = List.of("c1");

        repo.indexChunks(chunker.chunk(doc1, "corriere-brescia.it", "NEWS_ARTICLE", entities1));
        repo.indexChunks(chunker.chunk(doc2, "registro-imprese.it", "COMPANY_FILING", entities2));
        repo.indexChunks(chunker.chunk(doc3, "tribunale-brescia.it", "COURT_RECORD", entities3));
        repo.indexChunks(chunker.chunk(doc4, "gdf-report-2023.pdf", "OFFICIAL_REGISTRY", entities4));
        repo.indexChunks(chunker.chunk(doc5, "bilanci-ferretti.pdf", "COMPANY_FILING", entities5));

        var results = repo.search("appalti pubblici Ferretti conflitto interessi", 3);
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(3);
        // all results should have a score > 0
        results.forEach(r -> assertThat(r.score()).isGreaterThan(0.0f));
    }

    @Test
    void shouldReturnTopKResults() {
        var results = repo.search("corruzione costruzioni", 2);
        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            var f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject " + fieldName, e);
        }
    }
}
