package ai.investigator.domain.model;

public record Company(
        String id,
        String name,
        String registrationNumber,  // nullable
        String jurisdiction,
        String legalForm,           // e.g. "Srl", "SpA", "Ltd"
        boolean active,
        String vatNumber,           // nullable
        String registeredAddress,   // nullable
        String sector               // nullable — NACE/ATECO code or free text
) {}
