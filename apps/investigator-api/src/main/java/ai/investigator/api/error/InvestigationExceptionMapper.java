package ai.investigator.api.error;

import ai.investigator.domain.error.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class InvestigationExceptionMapper {

    @ExceptionHandler(InvestigationException.class)
    public ResponseEntity<Map<String, Object>> handleInvestigationException(InvestigationException e) {
        return toResponse(e.getError());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "INTERNAL_ERROR", "message", e.getMessage() != null ? e.getMessage() : "unknown"));
    }

    private ResponseEntity<Map<String, Object>> toResponse(InvestigationError error) {
        return switch (error) {
            case EntityNotFound(var type, var id) ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "ENTITY_NOT_FOUND",
                        "entityType", type, "identifier", id));
            case InsufficientEvidence(var claim, var reason) ->
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "INSUFFICIENT_EVIDENCE",
                        "claim", claim, "reason", reason));
            case SourceConflict(var claim, var s1, var s2) ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "SOURCE_CONFLICT",
                        "claim", claim, "source1", s1, "source2", s2));
            case GraphTraversalError(var query, var cause) ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GRAPH_TRAVERSAL_ERROR",
                        "query", query, "cause", cause));
        };
    }
}
