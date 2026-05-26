package ai.investigator.graph.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables Spring Data Neo4j repository scanning for this module.
 *
 * Driver and session factory are provided by Spring Boot's Neo4jAutoConfiguration
 * via {@code spring.neo4j.*} properties. We register an explicit
 * {@link Neo4jTransactionManager} so Neo4jTemplate can find it even when another
 * {@code PlatformTransactionManager} (JPA in investigator-web) is also on the
 * classpath — without this bean SDN 8's {@code Neo4jTemplate.transactionTemplate}
 * stays null and every @Query throws NPE.
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "ai.investigator.graph.repository")
@EnableTransactionManagement
public class GraphConfiguration {

    @Bean("neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}
