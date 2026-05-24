package ai.investigator.graph.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Neo4jConfig.class)
public class Neo4jConfiguration {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(Neo4jConfig config) {
        return GraphDatabase.driver(
            config.getUri(),
            AuthTokens.basic(config.getAuthentication().getUsername(),
                             config.getAuthentication().getPassword())
        );
    }
}
