package ai.investigator.domain.model;

import java.time.LocalDate;
import java.util.List;

public record Document(
        String id,
        String title,
        SourceType sourceType,
        String sourceUrl,         // nullable
        LocalDate publishedAt,    // nullable
        ConfidenceLevel reliability,
        String language,          // ISO 639-1, e.g. "it", "en"
        List<String> qdrantIds    // chunk IDs stored in the vector DB
) {

    public enum SourceType {
        NEWS_ARTICLE,
        COURT_RECORD,
        COMPANY_FILING,
        PARLIAMENTARY_ACT,
        LEAKED_DOCUMENT,
        OFFICIAL_REGISTRY
    }
}
