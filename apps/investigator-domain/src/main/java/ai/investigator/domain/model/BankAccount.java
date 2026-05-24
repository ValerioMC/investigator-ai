package ai.investigator.domain.model;

public record BankAccount(
        String id,
        String institution,
        String jurisdiction,
        String currency,
        String iban,   // nullable
        String swift   // nullable
) {}
