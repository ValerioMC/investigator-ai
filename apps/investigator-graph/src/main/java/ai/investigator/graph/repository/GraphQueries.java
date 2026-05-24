package ai.investigator.graph.repository;

/** All Cypher queries as constants. Never inline these in business logic. */
public final class GraphQueries {

    private GraphQueries() {}

    // --- Ownership & UBO ---

    public static final String FIND_UBO =
        """
        MATCH path = (p:Person)-[:OWNS|CONTROLS*1..5]->(c:Company {name: $companyName})
        RETURN p, length(path) AS distance, path
        ORDER BY distance
        """;

    public static final String FIND_COMPANIES_BY_PERSON =
        """
        MATCH (p:Person {fullName: $fullName})-[:OWNS|IS_DIRECTOR_OF|CONTROLS*1..4]->(c:Company)
        OPTIONAL MATCH (c)-[:REGISTERED_IN]->(j:Jurisdiction)
        RETURN DISTINCT c, j
        """;

    public static final String FIND_OWNERSHIP_CHAIN =
        """
        MATCH path = (p:Person)-[r:OWNS]->(c:Company {name: $companyName})
        RETURN p.fullName AS owner, r.sharePercent AS share,
               r.directOrIndirect AS ownershipType, p.nationality AS nationality
        ORDER BY r.sharePercent DESC
        """;

    public static final String FIND_COMPANY_CONTROLLERS =
        """
        MATCH (ctrl)-[:OWNS|CONTROLS]->(c:Company {name: $companyName})
        RETURN ctrl, labels(ctrl) AS nodeType
        """;

    // --- Conflict of interest ---

    public static final String DETECT_CONFLICT_OF_INTEREST =
        """
        MATCH (p:Person)-[:HELD_PUBLIC_ROLE]->(pb:PublicBody)-[:ISSUED]->(ct:Contract)
              -[:AWARDED_TO]->(co:Company)<-[:OWNS|IS_DIRECTOR_OF]-(p)
        WHERE ct.awardedAt >= date($from) AND ct.awardedAt <= date($to)
        RETURN p.fullName AS person, pb.name AS publicBody,
               ct.title AS contract, ct.amount AS amount, ct.awardedAt AS awardedAt,
               co.name AS company
        """;

    public static final String DETECT_CONFLICT_FOR_PERSON =
        """
        MATCH (p:Person {fullName: $personName})-[:HELD_PUBLIC_ROLE]->(pb:PublicBody)
              -[:ISSUED]->(ct:Contract)-[:AWARDED_TO]->(co:Company)
        WHERE (p)-[:OWNS|IS_DIRECTOR_OF]->(co)
        RETURN pb.name AS publicBody, ct.title AS contract,
               ct.amount AS amount, co.name AS company
        """;

    // --- Tax haven connections ---

    public static final String FIND_TAX_HAVEN_CONNECTIONS =
        """
        MATCH (p:Person)-[:OWNS|CONTROLS*1..3]->(c:Company)
              -[:REGISTERED_IN]->(j:Jurisdiction {taxHaven: true})
        WHERE p.fullName = $personName
        RETURN p.fullName AS person, c.name AS company,
               j.name AS jurisdiction, j.isoCode AS isoCode
        """;

    // --- Family and associations ---

    public static final String FIND_FAMILY_NETWORK =
        """
        MATCH (p:Person {fullName: $personName})-[r:FAMILY_RELATION]-(relative:Person)
        RETURN relative.fullName AS name, r.type AS relationshipType
        """;

    public static final String FIND_ASSOCIATED_PERSONS =
        """
        MATCH (p:Person {fullName: $personName})-[r:ASSOCIATED_WITH]-(assoc:Person)
        RETURN assoc.fullName AS name, r.context AS context, r.strength AS strength
        """;

    // --- Criminal records ---

    public static final String FIND_CONVICTIONS =
        """
        MATCH (p:Person {fullName: $personName})-[r:CONVICTED_OF]->(crime:Crime)
        RETURN crime.type AS type, crime.description AS description,
               crime.severity AS severity, crime.status AS status,
               r.year AS year, r.court AS court
        """;

    // --- Shortest path ---

    public static final String SHORTEST_PATH =
        """
        MATCH path = shortestPath(
          (a:Person {fullName: $person1})-[*..6]-(b:Person {fullName: $person2})
        )
        RETURN path, length(path) AS hops
        """;

    // --- Document references ---

    public static final String FIND_DOCUMENTS_FOR_PERSON =
        """
        MATCH (p:Person {fullName: $personName})-[r:MENTIONED_IN]->(d:Document)
        RETURN d.title AS title, d.sourceType AS sourceType,
               d.publishedAt AS publishedAt, r.context AS context,
               d.reliability AS reliability
        ORDER BY d.publishedAt DESC
        """;

    // --- Contract queries ---

    public static final String FIND_CONTRACTS_WON_BY_COMPANY =
        """
        MATCH (ct:Contract)-[:AWARDED_TO]->(c:Company {name: $companyName})
        RETURN ct.title AS title, ct.amount AS amount,
               ct.awardedAt AS awardedAt, ct.publicBodyName AS issuedBy
        ORDER BY ct.amount DESC
        """;

    // --- Financial flows ---

    public static final String FIND_SUSPICIOUS_TRANSACTIONS =
        """
        MATCH (t:Transaction {suspicious: true})-[:FROM]->(src:BankAccount)
              -[:HELD_IN]->(j:Jurisdiction)
        WHERE (p:Person {fullName: $personName})-[:OWNS_ACCOUNT]->(src)
        RETURN t.amount AS amount, t.currency AS currency,
               t.date AS date, j.name AS jurisdiction
        ORDER BY t.amount DESC
        """;

    // --- Write / ingest queries ---

    public static final String MERGE_PERSON =
        """
        MERGE (p:Person {id: $id})
        SET p.fullName = $fullName,
            p.birthDate = CASE WHEN $birthDate IS NOT NULL THEN date($birthDate) ELSE null END,
            p.nationality = $nationality,
            p.taxCode = $taxCode,
            p.politicalRole = $politicalRole,
            p.riskScore = $riskScore
        RETURN p
        """;

    public static final String MERGE_COMPANY =
        """
        MERGE (c:Company {id: $id})
        SET c.name = $name,
            c.registrationNumber = $registrationNumber,
            c.jurisdiction = $jurisdiction,
            c.legalForm = $legalForm,
            c.active = $active,
            c.vatNumber = $vatNumber,
            c.sector = $sector
        RETURN c
        """;

    public static final String MERGE_CONTRACT =
        """
        MERGE (ct:Contract {id: $id})
        SET ct.title = $title,
            ct.amount = $amount,
            ct.awardedAt = date($awardedAt),
            ct.cpvCode = $cpvCode,
            ct.publicBodyName = $publicBodyName,
            ct.source = $source
        RETURN ct
        """;

    public static final String MERGE_PUBLIC_BODY =
        """
        MERGE (pb:PublicBody {id: $id})
        SET pb.name = $name, pb.level = $level, pb.country = $country
        RETURN pb
        """;

    public static final String MERGE_JURISDICTION =
        """
        MERGE (j:Jurisdiction {isoCode: $isoCode})
        SET j.id = $id, j.name = $name,
            j.taxHaven = $taxHaven, j.euMember = $euMember
        RETURN j
        """;

    public static final String MERGE_OWNS =
        """
        MATCH (p:Person {fullName: $personName}), (c:Company {name: $companyName})
        MERGE (p)-[r:OWNS {sharePercent: $sharePercent}]->(c)
        SET r.directOrIndirect = $directOrIndirect
        """;

    public static final String MERGE_IS_DIRECTOR_OF =
        """
        MATCH (p:Person {fullName: $personName}), (c:Company {name: $companyName})
        MERGE (p)-[r:IS_DIRECTOR_OF {role: $role, from: date($from)}]->(c)
        SET r.to = CASE WHEN $to IS NOT NULL THEN date($to) ELSE null END
        """;

    public static final String MERGE_HELD_PUBLIC_ROLE =
        """
        MATCH (p:Person {fullName: $personName}), (pb:PublicBody {name: $bodyName})
        MERGE (p)-[r:HELD_PUBLIC_ROLE {title: $title, from: date($from)}]->(pb)
        SET r.to = CASE WHEN $to IS NOT NULL THEN date($to) ELSE null END
        """;

    public static final String MERGE_CONTRACT_AWARDED_TO =
        """
        MATCH (ct:Contract {title: $contractTitle}), (c:Company {name: $companyName})
        MERGE (ct)-[:AWARDED_TO]->(c)
        """;

    public static final String MERGE_PUBLIC_BODY_ISSUED_CONTRACT =
        """
        MATCH (pb:PublicBody {name: $bodyName}), (ct:Contract {title: $contractTitle})
        MERGE (pb)-[:ISSUED]->(ct)
        """;

    public static final String MERGE_REGISTERED_IN =
        """
        MATCH (c:Company {name: $companyName}), (j:Jurisdiction {isoCode: $isoCode})
        MERGE (c)-[:REGISTERED_IN]->(j)
        """;
}
