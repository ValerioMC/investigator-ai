package ai.investigator.web.dto;

import ai.investigator.web.entity.InvestigationSession;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SessionDto {

    public record CreateRequest(
        String query,
        int depth,
        List<String> focusEntities
    ) {
        public CreateRequest {
            if (query == null || query.isBlank())
                throw new IllegalArgumentException("query must not be blank");
            if (depth < 1 || depth > 10) depth = 3;
            if (focusEntities == null) focusEntities = List.of();
        }
    }

    public record Summary(
        UUID id,
        String query,
        int depth,
        String status,
        int findingCount,
        int highCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
    ) {
        public static Summary from(InvestigationSession s) {
            return new Summary(
                s.id, s.query, s.depth, s.status.name(),
                s.findingCount, s.highCount, s.createdAt, s.completedAt
            );
        }
    }

    public record Detail(
        UUID id,
        String query,
        int depth,
        List<String> focusEntities,
        String status,
        String report,
        int findingCount,
        int highCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
    ) {
        public static Detail from(InvestigationSession s) {
            return new Detail(
                s.id, s.query, s.depth,
                s.focusEntities != null ? Arrays.asList(s.focusEntities) : List.of(),
                s.status.name(), s.report,
                s.findingCount, s.highCount, s.errorMessage,
                s.createdAt, s.startedAt, s.completedAt
            );
        }
    }

    public record StatsResponse(
        long totalSessions,
        long sessionsLastHour,
        long completedSessions,
        long failedSessions
    ) {}
}
