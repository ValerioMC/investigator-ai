package ai.investigator.graph.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Enables Spring Data Neo4j repository scanning for this module.
 * Driver, session factory and transaction manager are provided by
 * {@code spring-boot-autoconfigure}'s Neo4jAutoConfiguration via the
 * {@code spring.neo4j.*} properties.
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "ai.investigator.graph.repository")
public class GraphConfiguration {
}
