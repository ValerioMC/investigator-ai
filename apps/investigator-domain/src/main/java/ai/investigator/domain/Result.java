package ai.investigator.domain;

import ai.investigator.domain.error.InvestigationError;

/**
 * Discriminated union for tool return values.
 * Prefer this over throwing exceptions from agent tools.
 *
 * <pre>{@code
 *   return switch (result) {
 *       case Result.Success<Person> s -> handle(s.value());
 *       case Result.Failure<Person> f -> handleError(f.error());
 *   };
 * }</pre>
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    record Success<T>(T value) implements Result<T> {}

    record Failure<T>(InvestigationError error) implements Result<T> {}

    static <T> Result<T> ok(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> fail(InvestigationError error) {
        return new Failure<>(error);
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }
}
