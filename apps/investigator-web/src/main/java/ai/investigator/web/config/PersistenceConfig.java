package ai.investigator.web.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Web module wires both JPA (PostgreSQL) and Neo4j. Once {@code GraphConfiguration}
 * registers a {@link org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager},
 * Spring Boot's JPA auto-config no longer registers a tx manager (its bean is
 * {@code @ConditionalOnMissingBean(PlatformTransactionManager.class)}). We re-add
 * it here and mark it {@code @Primary} so {@code @Transactional} on JPA repos
 * keeps working; the Neo4j manager is still discovered by SDN templates via
 * type-iteration.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
