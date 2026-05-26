package ai.investigator.graph;

import ai.investigator.graph.repository.GraphRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Cypher in {@link GraphRepository} against a real Neo4j 5 instance.
 * Boot 4 dropped the @DataNeo4jTest slice, so we boot a tiny app scoped to
 * this package and let SDN autoconfig + repository scanning do the rest.
 */
@Testcontainers
@SpringBootTest(classes = GraphRepositoryIT.TestApp.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphRepositoryIT {

    @SpringBootApplication
    static class TestApp {}

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.26-community"))
        .withoutAuthentication();

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "");
    }

    @Autowired GraphRepository repo;
    @Autowired Driver driver;

    @BeforeAll
    void loadFixtures() {
        try (Session s = driver.session()) {
            s.run("MATCH (n) DETACH DELETE n");
            s.run("""
                CREATE (p1:Person {id:'p1', fullName:'Marco Ferretti', nationality:'IT',
                    politicalRole:'Consigliere comunale', riskScore: 0.8})
                CREATE (p2:Person {id:'p2', fullName:'Giulia Morandi', nationality:'IT'})

                CREATE (c1:Company {id:'c1', name:'Costruzioni Ferretti Srl',
                    jurisdiction:'IT', legalForm:'Srl', active:true})
                CREATE (c2:Company {id:'c2', name:'LuxHold SA',
                    jurisdiction:'LU', legalForm:'SA', active:true})
                CREATE (c3:Company {id:'c3', name:'Edilizia Morandi SpA',
                    jurisdiction:'IT', legalForm:'SpA', active:true})

                CREATE (j1:Jurisdiction {id:'j1', name:'Luxembourg', isoCode:'LU',
                    taxHaven:true, euMember:true})

                CREATE (pb:PublicBody {id:'pb1', name:'Comune di Brescia',
                    level:'MUNICIPAL', country:'IT'})

                CREATE (ct1:Contract {id:'ct1', title:'Riqualificazione Piazza Loggia',
                    amount:1200000.0, awardedAt:date('2021-03-15'),
                    publicBodyName:'Comune di Brescia', source:'ANAC'})
                CREATE (ct2:Contract {id:'ct2', title:'Manutenzione strade comunali',
                    amount:450000.0, awardedAt:date('2022-07-01'),
                    publicBodyName:'Comune di Brescia', source:'ANAC'})

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
        assertThat(result.stream().map(p -> p.fullName()).toList())
            .contains("Marco Ferretti");
    }

    @Test
    void findCompaniesByPerson_shouldReturnAllControlledCompanies() {
        var companies = repo.findCompaniesByPerson("Marco Ferretti");
        assertThat(companies).isNotEmpty();
        assertThat(companies.stream().map(c -> c.name()).toList())
            .contains("Costruzioni Ferretti Srl");
    }

    @Test
    void detectConflictsOfInterest_shouldDetectFerretti() {
        var conflicts = repo.detectConflictsOfInterest("2020-01-01", "2023-12-31");
        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.stream().map(c -> c.person()).toList())
            .contains("Marco Ferretti");
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
    void findContractsWonByCompany_shouldReturnBothContracts() {
        var contracts = repo.findContractsWonByCompany("Costruzioni Ferretti Srl");
        assertThat(contracts).hasSize(2);
        double total = contracts.stream().mapToDouble(c -> c.amount()).sum();
        assertThat(total).isEqualTo(1650000.0);
    }
}
