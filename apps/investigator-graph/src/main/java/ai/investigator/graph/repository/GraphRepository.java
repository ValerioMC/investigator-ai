package ai.investigator.graph.repository;

import ai.investigator.graph.entity.CompanyNode;
import ai.investigator.graph.entity.PersonNode;
import ai.investigator.graph.projection.AssociationEntry;
import ai.investigator.graph.projection.ConflictEntry;
import ai.investigator.graph.projection.ContractEntry;
import ai.investigator.graph.projection.ConvictionEntry;
import ai.investigator.graph.projection.DocumentRef;
import ai.investigator.graph.projection.FamilyEntry;
import ai.investigator.graph.projection.OwnershipEntry;
import ai.investigator.graph.projection.PathResult;
import ai.investigator.graph.projection.TaxHavenEntry;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Single repository for the whole graph. Marker type is PersonNode (irrelevant —
 * every query uses @Query and returns its own projection).
 *
 * Read methods return either entity records ({@link PersonNode}, {@link CompanyNode})
 * or DTO projections from {@code ai.investigator.graph.projection}. SDN binds
 * Cypher RETURN aliases to constructor parameters by name, so projection records
 * must have field names matching the RETURN aliases exactly.
 *
 * Write methods are plain {@link Query} statements — no entity {@code save()}
 * because we want explicit MERGE semantics and we don't round-trip full nodes.
 */
@Repository
public interface GraphRepository extends Neo4jRepository<PersonNode, String> {

    // -----------------------------------------------------------------
    // Reads — ownership / UBO
    // -----------------------------------------------------------------

    // SDN 8 routes RETURN DISTINCT <node> through DtoInstantiatingConverter, which
    // tries to bind RETURN aliases to record components and crashes with all-nulls
    // when the alias is the bare node name. Returning each property explicitly with
    // its constructor-parameter alias makes the projection deterministic.
    @Query("""
        MATCH path = (p:Person)-[:OWNS|CONTROLS*1..5]->(c:Company {name: $companyName})
        WITH p, min(length(path)) AS hops
        RETURN p.id AS id, p.fullName AS fullName, p.birthDate AS birthDate,
               p.nationality AS nationality, p.taxCode AS taxCode,
               p.politicalRole AS politicalRole, p.riskScore AS riskScore
        ORDER BY hops
        """)
    List<PersonNode> findUBO(@Param("companyName") String companyName);

    @Query("""
        MATCH (p:Person {fullName: $fullName})-[:OWNS|IS_DIRECTOR_OF|CONTROLS*1..4]->(c:Company)
        RETURN DISTINCT c.id AS id, c.name AS name, c.registrationNumber AS registrationNumber,
                        c.jurisdiction AS jurisdiction, c.legalForm AS legalForm,
                        coalesce(c.active, true) AS active,
                        c.vatNumber AS vatNumber, c.sector AS sector
        """)
    List<CompanyNode> findCompaniesByPerson(@Param("fullName") String fullName);

    @Query("""
        CALL {
          MATCH (p:Person)-[r:OWNS]->(c:Company {name: $companyName})
          RETURN p.fullName AS ownerName, r.sharePercent AS sharePercent,
                 coalesce(r.directOrIndirect, 'DIRECT') AS ownershipType,
                 p.nationality AS nationality
          UNION
          MATCH (holding:Company)-[:CONTROLS]->(:Company {name: $companyName})
          MATCH (p:Person)-[r:OWNS]->(holding)
          RETURN p.fullName AS ownerName, r.sharePercent AS sharePercent,
                 'INDIRECT via ' + holding.name AS ownershipType,
                 p.nationality AS nationality
        }
        RETURN ownerName, sharePercent, ownershipType, nationality
        ORDER BY sharePercent DESC
        """)
    List<OwnershipEntry> findOwnershipChain(@Param("companyName") String companyName);

    // -----------------------------------------------------------------
    // Reads — conflict of interest
    // -----------------------------------------------------------------

    @Query("""
        MATCH (p:Person)-[:HELD_PUBLIC_ROLE]->(pb:PublicBody)-[:ISSUED]->(ct:Contract)
              -[:AWARDED_TO]->(co:Company)<-[:OWNS|IS_DIRECTOR_OF]-(p)
        WHERE ct.awardedAt >= date($from) AND ct.awardedAt <= date($to)
        RETURN p.fullName AS person, pb.name AS publicBody,
               ct.title AS contract, ct.amount AS amount, ct.awardedAt AS awardedAt,
               co.name AS company
        """)
    List<ConflictEntry> detectConflictsOfInterest(@Param("from") String from,
                                                  @Param("to") String to);

    @Query("""
        MATCH (p:Person {fullName: $personName})-[:HELD_PUBLIC_ROLE]->(pb:PublicBody)
              -[:ISSUED]->(ct:Contract)-[:AWARDED_TO]->(co:Company)
        WHERE (p)-[:OWNS|IS_DIRECTOR_OF]->(co)
           OR (p)-[:FAMILY_RELATION]-(:Person)-[:OWNS|IS_DIRECTOR_OF]->(co)
           OR (p)-[:FAMILY_RELATION]-(:Person)-[:OWNS]->(:Company)-[:CONTROLS]->(co)
        RETURN $personName AS person, pb.name AS publicBody,
               ct.title AS contract, ct.amount AS amount,
               ct.awardedAt AS awardedAt, co.name AS company
        """)
    List<ConflictEntry> detectConflictsForPerson(@Param("personName") String personName);

    @Query("""
        MATCH (p:Person)-[:HELD_PUBLIC_ROLE]->(pb:PublicBody)
              -[:ISSUED]->(ct:Contract)-[:AWARDED_TO]->(co:Company)
        WHERE ct.awardedAt >= date($from) AND ct.awardedAt <= date($to)
          AND (
            (p)-[:OWNS|IS_DIRECTOR_OF]->(co)
            OR (p)-[:FAMILY_RELATION]-(:Person)-[:OWNS|IS_DIRECTOR_OF]->(co)
            OR (p)-[:FAMILY_RELATION]-(:Person)-[:OWNS]->(:Company)-[:CONTROLS]->(co)
          )
        RETURN p.fullName AS person, pb.name AS publicBody,
               ct.title AS contract, ct.amount AS amount,
               ct.awardedAt AS awardedAt, co.name AS company
        ORDER BY ct.awardedAt
        """)
    List<ConflictEntry> detectConflictsInRange(@Param("from") String from,
                                               @Param("to") String to);

    // -----------------------------------------------------------------
    // Reads — tax havens / family / convictions
    // -----------------------------------------------------------------

    @Query("""
        MATCH (p:Person {fullName: $personName})-[:OWNS|CONTROLS*1..3]->(c:Company)
              -[:REGISTERED_IN]->(j:Jurisdiction {taxHaven: true})
        RETURN p.fullName AS person, c.name AS company,
               j.name AS jurisdiction, j.isoCode AS isoCode
        """)
    List<TaxHavenEntry> findTaxHavenConnections(@Param("personName") String personName);

    @Query("""
        MATCH (p:Person {fullName: $personName})-[r:FAMILY_RELATION]-(relative:Person)
        RETURN relative.fullName AS name, r.type AS relationshipType
        """)
    List<FamilyEntry> findFamilyNetwork(@Param("personName") String personName);

    @Query("""
        MATCH (p:Person {fullName: $personName})-[r:ASSOCIATED_WITH]-(assoc:Person)
        RETURN assoc.fullName AS name,
               coalesce(r.context, 'unknown') AS context,
               coalesce(r.strength, 'unknown') AS strength
        """)
    List<AssociationEntry> findAssociatedPersons(@Param("personName") String personName);

    @Query("""
        MATCH (p:Person {fullName: $personName})-[r:CONVICTED_OF]->(crime:Crime)
        RETURN crime.type AS type, crime.description AS description,
               crime.severity AS severity, crime.status AS status,
               r.year AS year, r.court AS court
        """)
    List<ConvictionEntry> findConvictions(@Param("personName") String personName);

    // -----------------------------------------------------------------
    // Reads — contracts / documents
    // -----------------------------------------------------------------

    @Query("""
        MATCH (ct:Contract)-[:AWARDED_TO]->(c:Company {name: $companyName})
        RETURN ct.title AS title, ct.amount AS amount,
               ct.awardedAt AS awardedAt, ct.publicBodyName AS issuedBy
        ORDER BY ct.amount DESC
        """)
    List<ContractEntry> findContractsWonByCompany(@Param("companyName") String companyName);

    @Query("""
        MATCH (p:Person {fullName: $personName})-[r:MENTIONED_IN]->(d:Document)
        RETURN d.title AS title, d.sourceType AS sourceType,
               d.publishedAt AS publishedAt,
               coalesce(r.context, '') AS context,
               coalesce(d.reliability, 'UNVERIFIED') AS reliability
        ORDER BY d.publishedAt DESC
        """)
    List<DocumentRef> findDocumentsForPerson(@Param("personName") String personName);

    // -----------------------------------------------------------------
    // Reads — shortest path (returns hops + list of node display names)
    // -----------------------------------------------------------------

    @Query("""
        MATCH path = shortestPath(
          (a:Person {fullName: $person1})-[*..6]-(b:Person {fullName: $person2})
        )
        RETURN length(path) AS hops,
               [n IN nodes(path) | coalesce(n.fullName, n.name, n.title, labels(n)[0])] AS nodeNames
        """)
    Optional<PathResult> shortestPath(@Param("person1") String person1,
                                      @Param("person2") String person2);

    @Query("""
        MATCH path = shortestPath(
          (a:Person {fullName: $personName})-[*..6]-(b:Company {name: $companyName})
        )
        RETURN length(path) AS hops,
               [n IN nodes(path) | coalesce(n.fullName, n.name, n.title, labels(n)[0])] AS nodeNames
        """)
    Optional<PathResult> personToCompanyPath(@Param("personName") String personName,
                                             @Param("companyName") String companyName);

    // -----------------------------------------------------------------
    // Reads — catalog
    // -----------------------------------------------------------------

    @Query("MATCH (p:Person) WHERE p.fullName IS NOT NULL RETURN p.fullName")
    List<String> listAllPersonNames();

    @Query("MATCH (c:Company) WHERE c.name IS NOT NULL RETURN c.name")
    List<String> listAllCompanyNames();

    // -----------------------------------------------------------------
    // Writes — explicit MERGEs (idempotent ingest)
    // -----------------------------------------------------------------

    @Query("""
        MERGE (p:Person {id: $id})
        SET p.fullName = $fullName,
            p.birthDate = CASE WHEN $birthDate IS NOT NULL THEN date($birthDate) ELSE null END,
            p.nationality = $nationality,
            p.taxCode = $taxCode,
            p.politicalRole = $politicalRole,
            p.riskScore = $riskScore
        """)
    void mergePerson(@Param("id") String id,
                     @Param("fullName") String fullName,
                     @Param("birthDate") String birthDate,
                     @Param("nationality") String nationality,
                     @Param("taxCode") String taxCode,
                     @Param("politicalRole") String politicalRole,
                     @Param("riskScore") Double riskScore);

    @Query("""
        MERGE (c:Company {id: $id})
        SET c.name = $name,
            c.registrationNumber = $registrationNumber,
            c.jurisdiction = $jurisdiction,
            c.legalForm = $legalForm,
            c.active = $active,
            c.vatNumber = $vatNumber,
            c.sector = $sector
        """)
    void mergeCompany(@Param("id") String id,
                      @Param("name") String name,
                      @Param("registrationNumber") String registrationNumber,
                      @Param("jurisdiction") String jurisdiction,
                      @Param("legalForm") String legalForm,
                      @Param("active") boolean active,
                      @Param("vatNumber") String vatNumber,
                      @Param("sector") String sector);

    @Query("""
        MERGE (ct:Contract {id: $id})
        SET ct.title = $title,
            ct.amount = $amount,
            ct.awardedAt = date($awardedAt),
            ct.cpvCode = $cpvCode,
            ct.publicBodyName = $publicBodyName,
            ct.source = $source
        """)
    void mergeContract(@Param("id") String id,
                       @Param("title") String title,
                       @Param("amount") double amount,
                       @Param("awardedAt") String awardedAt,
                       @Param("cpvCode") String cpvCode,
                       @Param("publicBodyName") String publicBodyName,
                       @Param("source") String source);

    @Query("""
        MERGE (pb:PublicBody {id: $id})
        SET pb.name = $name, pb.level = $level, pb.country = $country
        """)
    void mergePublicBody(@Param("id") String id,
                         @Param("name") String name,
                         @Param("level") String level,
                         @Param("country") String country);

    @Query("""
        MERGE (j:Jurisdiction {isoCode: $isoCode})
        SET j.id = $id, j.name = $name,
            j.taxHaven = $taxHaven, j.euMember = $euMember
        """)
    void mergeJurisdiction(@Param("id") String id,
                           @Param("name") String name,
                           @Param("isoCode") String isoCode,
                           @Param("taxHaven") boolean taxHaven,
                           @Param("euMember") boolean euMember);

    @Query("""
        MATCH (p:Person {fullName: $personName}), (c:Company {name: $companyName})
        MERGE (p)-[r:OWNS {sharePercent: $sharePercent}]->(c)
        SET r.directOrIndirect = $directOrIndirect
        """)
    void mergeOwns(@Param("personName") String personName,
                   @Param("companyName") String companyName,
                   @Param("sharePercent") double sharePercent,
                   @Param("directOrIndirect") String directOrIndirect);

    @Query("""
        MATCH (p:Person {fullName: $personName}), (c:Company {name: $companyName})
        MERGE (p)-[r:IS_DIRECTOR_OF {role: $role, from: date($from)}]->(c)
        SET r.to = CASE WHEN $to IS NOT NULL THEN date($to) ELSE null END
        """)
    void mergeIsDirectorOf(@Param("personName") String personName,
                           @Param("companyName") String companyName,
                           @Param("role") String role,
                           @Param("from") String from,
                           @Param("to") String to);

    @Query("""
        MATCH (p:Person {fullName: $personName}), (pb:PublicBody {name: $bodyName})
        MERGE (p)-[r:HELD_PUBLIC_ROLE {title: $title, from: date($from)}]->(pb)
        SET r.to = CASE WHEN $to IS NOT NULL THEN date($to) ELSE null END
        """)
    void mergeHeldPublicRole(@Param("personName") String personName,
                             @Param("bodyName") String bodyName,
                             @Param("title") String title,
                             @Param("from") String from,
                             @Param("to") String to);

    @Query("""
        MATCH (ct:Contract {title: $contractTitle}), (c:Company {name: $companyName})
        MERGE (ct)-[:AWARDED_TO]->(c)
        """)
    void mergeContractAwardedTo(@Param("contractTitle") String contractTitle,
                                @Param("companyName") String companyName);

    @Query("""
        MATCH (pb:PublicBody {name: $bodyName}), (ct:Contract {title: $contractTitle})
        MERGE (pb)-[:ISSUED]->(ct)
        """)
    void mergePublicBodyIssuedContract(@Param("bodyName") String bodyName,
                                       @Param("contractTitle") String contractTitle);

    @Query("""
        MATCH (c:Company {name: $companyName}), (j:Jurisdiction {isoCode: $isoCode})
        MERGE (c)-[:REGISTERED_IN]->(j)
        """)
    void mergeRegisteredIn(@Param("companyName") String companyName,
                           @Param("isoCode") String isoCode);
}
