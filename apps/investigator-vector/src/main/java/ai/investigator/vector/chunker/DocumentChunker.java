package ai.investigator.vector.chunker;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits raw text into overlapping chunks suitable for embedding.
 * Uses word-boundary splitting as a proxy for token count (1 word ≈ 1.3 tokens on average).
 */
@Component
public class DocumentChunker {

    // Approximate: 512 tokens ≈ 390 words; 64 overlap ≈ 49 words
    private static final int CHUNK_WORDS = 390;
    private static final int OVERLAP_WORDS = 49;

    public record DocumentChunk(
        String id,
        String text,
        Map<String, Object> metadata
    ) {}

    public List<DocumentChunk> chunk(String text, String source, String sourceType,
                                      List<String> entityIds) {
        String[] words = text.split("\\s+");
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + CHUNK_WORDS, words.length);
            String chunkText = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));

            chunks.add(new DocumentChunk(
                UUID.randomUUID().toString(),
                chunkText,
                Map.of(
                    "source", source,
                    "source_type", sourceType,
                    "entity_ids", entityIds,
                    "ingested_at", Instant.now().toString(),
                    "chunk_index", chunks.size()
                )
            ));

            if (end == words.length) break;
            start = end - OVERLAP_WORDS;
        }

        return chunks;
    }
}
