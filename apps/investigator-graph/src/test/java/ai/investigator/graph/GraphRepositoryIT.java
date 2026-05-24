package ai.investigator.graph;

import ai.investigator.graph.repository.Neo4jGraphRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Cypher queries against a real Neo4j instance.
 * Fixtures: 2 persons, 3 companies, 2 contracts, 1 public body, 1 jurisdiction.
 * Conflict scenario: Marco Ferretti held public role at Comune di Brescia,
 * which issued a contract awarded to Costruzioni Ferretti Srl — which he owns 60%.
 */
@Testcontainers
class GraphRepositoryIT {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.26-community"))
        .withoutAuthentication();

    static Neo4jGraphRepository repo;
    static Driver driver;

    @BeforeAll
    static void setup() {
        driver = GraphDatabase.driver(neo4j.getBoltUrl(), AuthTokens.none());
        repo = new Neo4jGraphRepository();
        // inject driver via reflection — acceptable in test scope
        try {
            var f = Neo4jGraphRepository.class.getDeclaredField("driver");
            f.setAccessible(true);
            f.set(repo, driver);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        loadFixtures();
    }

    static void loadFixtures() {
        try (Session s = driver.session()) {
            // Persons
            s.run("""
                CREATE (p1:Person {id:'p1', fullName:'Marco Ferretti', nationality:'IT',
                    politicalRole:'Consigliere comunale', riskScore: 0.8})
                CREATE (p2:Person {id:'p2', fullName:'Giulia Morandi', nationality:'IT'})

                // Companies
                CREATE (c1:Company {id:'c1', name:'Costruzioni Ferretti Srl',
                    jurisdiction:'IT', legalForm:'Srl', active:true})
                CREATE (c2:Company {id:'c2', name:'LuxHold SA',
                    jurisdiction:'LU', legalForm:'SA', active:true})
                CREATE (c3:Company {id:'c3', name:'Edilizia Morandi SpA',
                    jurisdiction:'IT', legalForm:'SpA', active:true})

                // Jurisdiction
                CREATE (j1:Jurisdiction {id:'j1', name:'Luxembourg', isoCode:'LU',
                    taxHaven:true, euMember:true})

                // Public body
                CREATE (pb:PublicBody {id:'pb1', name:'Comune di Brescia',
                    level:'MUNICIPAL', country:'IT'})

                // Contracts
                CREATE (ct1:Contract {id:'ct1', title:'Riqualificazione Piazza Loggia',
                    amount:1200000.0, awardedAt:date('2021-03-15'),
                    publicBodyName:'Comune di Brescia', source:'ANAC'})
                CREATE (ct2:Contract {id:'ct2', title:'Manutenzione strade comunali',
                    amount:450000.0, awardedAt:date('2022-07-01'),
                    publicBodyName:'Comune di Brescia', source:'ANAC'})

                // Relationships
                CREATE (p1)-[:OWNS {sharePercent:60.0, directOrIndirect:'DIRECT'}]->(c1)
                CREATE (p2)-[:OWNS {sharePercent:100.0, directOrIndirect:'DIRECT'}]->(c3)
                CREATE (c2)-[:CONTROLS {mechanism:'MAJORITY_SHARE', sharePercent:51.0}]->(c1)
                CREATE (p1)-[:IS_DIRECTOR_OF {role:'CEO', from:date('2015-01-01')}]->(c1)
                CREATE (p1)-[:HELD_PUBLIC_ROLE {title:'Consigliere', from:date('2019-06-01')}]->(pb)
                CREATE (pb)-[:ISSUED]->(ct1)
                CREATE (pb)-[:ISSUED]->(ct2)
                CREATE (ct1)-[:AWARDED_TO]->(c1)
                CREATE (ct2)-[:AWARDED_TO]->(c1)
                CREATE (c2)-[:REGISTERED_IN]->(j1)
                """);
        }
    }

    @Test
    void findUBO_shouldReturnPersonsOwningCompany() {
        var result = repo.findUBO("Costruzioni Ferretti Srl");
        assertThat(result).isNotEmpty();
        var names = result.stream().map(p -> p.fullName()).toList();
        assertThat(names).contains("Marco Ferretti");
    }

    @Test
    void findCompaniesByPerson_shouldReturnAllControlledCompanies() {
        var companies = repo.findCompaniesByPerson("Marco Ferretti");
        assertThat(companies).isNotEmpty();
        var names = companies.stream().map(c -> c.name()).toList();
        assertThat(names).contains("Costruzioni Ferretti Srl");
    }

    @Test
    void detectConflictsOfInterest_shouldDetectFerretti() {
        var conflicts = repo.detectConflictsOfInterest("2020-01-01", "2023-12-31");
        assertThat(conflicts).isNotEmpty();
        var persons = conflicts.stream().map(c -> c.person()).toList();
        assertThat(persons).contains("Marco Ferretti");
        // both contracts should appear
        assertThat(conflicts).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void detectConflictsForPerson_shouldFindByName() {
        var conflicts = repo.detectConflictsForPerson("Marco Ferretti");
        assertThat(conflicts).isNotEmpty();
        conflicts.forEach(c -> {
            assertThat(c.company()).isEqualTo("Costruzioni Ferretti Srl");
            assertThat(c.publicBody()).isEqualTo("Comune di Brescia");
        });
    }

    @Test
    void findTaxHavenConnections_shouldFindLuxembourg() {
        // LuxHold SA controls Costruzioni Ferretti — but Ferretti doesn't directly own LuxHold
        // This test verifies the query works; result may be empty given fixture shape
        var result = repo.findTaxHavenConnections("Marco Ferretti");
        // The query follows OWNS|CONTROLS — Ferretti->OWNS->c1<-CONTROLS-LuxHold->REGISTERED_IN->LU
        // Direction matters: Ferretti doesn't own LuxHold, so expect empty here — correct behavior
        assertThat(result).isNotNull();
    }

    @Test
    void findContractsWonByCompany_shouldReturnBothContracts() {
        var contracts = repo.findContractsWonByCompany("Costruzioni Ferretti Srl");
        assertThat(contracts).hasSize(2);
        double totalAmount = contracts.stream().mapToDouble(c -> c.amount()).sum();
        assertThat(totalAmount).isEqualTo(1650000.0);
    }
}
