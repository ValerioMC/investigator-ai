package ai.investigator.graph.repository;

import ai.investigator.graph.entity.CompanyNode;
import ai.investigator.graph.entity.ContractNode;
import ai.investigator.graph.entity.PersonNode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class Neo4jGraphRepository {

    private final Driver driver;

    public Neo4jGraphRepository(Driver driver) {
        this.driver = driver;
    }

    // --- UBO / Ownership ---

    public List<PersonNode> findUBO(String companyName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_UBO, Map.of("companyName", companyName))
                .list(r -> PersonNode.from(r.get("p").asNode()));
        }
    }

    public List<CompanyNode> findCompaniesByPerson(String fullName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_COMPANIES_BY_PERSON, Map.of("fullName", fullName))
                .list(r -> CompanyNode.from(r.get("c").asNode()));
        }
    }

    public record OwnershipEntry(
        String ownerName, double sharePercent, String ownershipType, String nationality
    ) {}

    public List<OwnershipEntry> findOwnershipChain(String companyName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_OWNERSHIP_CHAIN, Map.of("companyName", companyName))
                .list(r -> new OwnershipEntry(
                    r.get("owner").asString(),
                    r.get("share").asDouble(0.0),
                    r.get("ownershipType").asString("DIRECT"),
                    r.get("nationality").asString("unknown")
                ));
        }
    }

    // --- Conflict of interest ---

    public record ConflictEntry(
        String person, String publicBody, String contract, double amount,
        LocalDate awardedAt, String company
    ) {}

    public List<ConflictEntry> detectConflictsOfInterest(String from, String to) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.DETECT_CONFLICT_OF_INTEREST,
                    Map.of("from", from != null ? from : "1900-01-01",
                           "to",   to   != null ? to   : "2100-01-01"))
                .list(r -> new ConflictEntry(
                    r.get("person").asString(),
                    r.get("publicBody").asString(),
                    r.get("contract").asString(),
                    r.get("amount").asDouble(0.0),
                    readLocalDate(r.get("awardedAt")),
                    r.get("company").asString()
                ));
        }
    }

    public List<ConflictEntry> detectConflictsForPerson(String personName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.DETECT_CONFLICT_FOR_PERSON,
                    Map.of("personName", personName))
                .list(r -> new ConflictEntry(
                    personName,
                    r.get("publicBody").asString(),
                    r.get("contract").asString(),
                    r.get("amount").asDouble(0.0),
                    null,
                    r.get("company").asString()
                ));
        }
    }

    // --- Tax havens ---

    public record TaxHavenEntry(String person, String company, String jurisdiction, String isoCode) {}

    public List<TaxHavenEntry> findTaxHavenConnections(String personName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_TAX_HAVEN_CONNECTIONS,
                    Map.of("personName", personName))
                .list(r -> new TaxHavenEntry(
                    r.get("person").asString(),
                    r.get("company").asString(),
                    r.get("jurisdiction").asString(),
                    r.get("isoCode").asString()
                ));
        }
    }

    // --- Person profile ---

    public record FamilyEntry(String name, String relationshipType) {}

    public List<FamilyEntry> findFamilyNetwork(String personName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_FAMILY_NETWORK, Map.of("personName", personName))
                .list(r -> new FamilyEntry(r.get("name").asString(), r.get("relationshipType").asString()));
        }
    }

    public record AssociationEntry(String name, String context, String strength) {}

    public List<AssociationEntry> findAssociatedPersons(String personName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_ASSOCIATED_PERSONS, Map.of("personName", personName))
                .list(r -> new AssociationEntry(
                    r.get("name").asString(),
                    r.get("context").asString("unknown"),
                    r.get("strength").asString("unknown")
                ));
        }
    }

    public record ConvictionEntry(
        String type, String description, String severity, String status, int year, String court
    ) {}

    public List<ConvictionEntry> findConvictions(String personName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_CONVICTIONS, Map.of("personName", personName))
                .list(r -> new ConvictionEntry(
                    r.get("type").asString(),
                    r.get("description").asString(),
                    r.get("severity").asString(),
                    r.get("status").asString(),
                    r.get("year").asInt(0),
                    r.get("court").asString()
                ));
        }
    }

    // --- Contracts ---

    public record ContractEntry(String title, double amount, LocalDate awardedAt, String issuedBy) {}

    public List<ContractEntry> findContractsWonByCompany(String companyName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_CONTRACTS_WON_BY_COMPANY,
                    Map.of("companyName", companyName))
                .list(r -> new ContractEntry(
                    r.get("title").asString(),
                    r.get("amount").asDouble(0.0),
                    readLocalDate(r.get("awardedAt")),
                    r.get("issuedBy").asString()
                ));
        }
    }

    // --- Document references ---

    public record DocumentRef(
        String title, String sourceType, LocalDate publishedAt, String context, String reliability
    ) {}

    public List<DocumentRef> findDocumentsForPerson(String personName) {
        try (Session session = driver.session()) {
            return session.run(GraphQueries.FIND_DOCUMENTS_FOR_PERSON,
                    Map.of("personName", personName))
                .list(r -> new DocumentRef(
                    r.get("title").asString(),
                    r.get("sourceType").asString(),
                    readLocalDate(r.get("publishedAt")),
                    r.get("context").asString(""),
                    r.get("reliability").asString("UNVERIFIED")
                ));
        }
    }

    // --- Shortest path ---

    public record PathResult(int hops, List<String> nodeNames) {}

    public PathResult shortestPath(String person1, String person2) {
        try (Session session = driver.session()) {
            var result = session.run(GraphQueries.SHORTEST_PATH,
                Map.of("person1", person1, "person2", person2));
            if (!result.hasNext()) return null;
            Record r = result.next();
            int hops = r.get("hops").asInt();
            var path = r.get("path").asPath();
            var names = new java.util.ArrayList<String>();
            path.nodes().forEach(n -> {
                if (n.containsKey("fullName")) names.add(n.get("fullName").asString());
                else if (n.containsKey("name")) names.add(n.get("name").asString());
            });
            return new PathResult(hops, names);
        }
    }

    // --- Helpers ---

    // Neo4j stores dates as DATE type but legacy seed data may store them as strings.
    private static LocalDate readLocalDate(org.neo4j.driver.Value v) {
        if (v == null || v.isNull()) return null;
        try {
            return v.asLocalDate();
        } catch (Exception e) {
            var s = v.asString();
            return s.isBlank() ? null : LocalDate.parse(s);
        }
    }

    // --- Write / Ingest ---

    public void mergePerson(String id, String fullName, LocalDate birthDate, String nationality,
                             String taxCode, String politicalRole, Double riskScore) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_PERSON, Map.of(
                "id", id, "fullName", fullName,
                "birthDate", birthDate != null ? birthDate.toString() : Values.NULL,
                "nationality", nationality != null ? nationality : Values.NULL,
                "taxCode", taxCode != null ? taxCode : Values.NULL,
                "politicalRole", politicalRole != null ? politicalRole : Values.NULL,
                "riskScore", riskScore != null ? riskScore : Values.NULL
            ));
        }
    }

    public void mergeCompany(String id, String name, String registrationNumber,
                              String jurisdiction, String legalForm, boolean active,
                              String vatNumber, String sector) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_COMPANY, Map.of(
                "id", id, "name", name,
                "registrationNumber", registrationNumber != null ? registrationNumber : Values.NULL,
                "jurisdiction", jurisdiction, "legalForm", legalForm,
                "active", active,
                "vatNumber", vatNumber != null ? vatNumber : Values.NULL,
                "sector", sector != null ? sector : Values.NULL
            ));
        }
    }

    public void mergeContract(String id, String title, double amount, String awardedAt,
                               String cpvCode, String publicBodyName, String source) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_CONTRACT, Map.of(
                "id", id, "title", title, "amount", amount, "awardedAt", awardedAt,
                "cpvCode", cpvCode != null ? cpvCode : Values.NULL,
                "publicBodyName", publicBodyName, "source", source
            ));
        }
    }

    public void mergePublicBody(String id, String name, String level, String country) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_PUBLIC_BODY,
                Map.of("id", id, "name", name, "level", level, "country", country));
        }
    }

    public void mergeJurisdiction(String id, String name, String isoCode,
                                   boolean taxHaven, boolean euMember) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_JURISDICTION,
                Map.of("id", id, "name", name, "isoCode", isoCode,
                    "taxHaven", taxHaven, "euMember", euMember));
        }
    }

    public void mergeOwns(String personName, String companyName,
                           double sharePercent, String directOrIndirect) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_OWNS,
                Map.of("personName", personName, "companyName", companyName,
                    "sharePercent", sharePercent, "directOrIndirect", directOrIndirect));
        }
    }

    public void mergeIsDirectorOf(String personName, String companyName,
                                   String role, String from, String to) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_IS_DIRECTOR_OF,
                Map.of("personName", personName, "companyName", companyName,
                    "role", role, "from", from, "to", to != null ? to : Values.NULL));
        }
    }

    public void mergeHeldPublicRole(String personName, String bodyName,
                                     String title, String from, String to) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_HELD_PUBLIC_ROLE,
                Map.of("personName", personName, "bodyName", bodyName,
                    "title", title, "from", from, "to", to != null ? to : Values.NULL));
        }
    }

    public void mergeContractAwardedTo(String contractTitle, String companyName) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_CONTRACT_AWARDED_TO,
                Map.of("contractTitle", contractTitle, "companyName", companyName));
        }
    }

    public void mergePublicBodyIssuedContract(String bodyName, String contractTitle) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_PUBLIC_BODY_ISSUED_CONTRACT,
                Map.of("bodyName", bodyName, "contractTitle", contractTitle));
        }
    }

    public void mergeRegisteredIn(String companyName, String isoCode) {
        try (Session session = driver.session()) {
            session.run(GraphQueries.MERGE_REGISTERED_IN,
                Map.of("companyName", companyName, "isoCode", isoCode));
        }
    }
}
