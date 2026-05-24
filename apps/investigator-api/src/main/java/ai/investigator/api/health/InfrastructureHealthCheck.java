package ai.investigator.api.health;

import org.neo4j.driver.Driver;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;

@Component
public class InfrastructureHealthCheck implements HealthIndicator {

    private final Driver neo4jDriver;

    public InfrastructureHealthCheck(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public Health health() {
        boolean neo4jOk = checkNeo4j();
        boolean qdrantOk = checkQdrant();

        Health.Builder builder = (neo4jOk && qdrantOk) ? Health.up() : Health.down();
        return builder
            .withDetail("neo4j", neo4jOk ? "UP" : "DOWN")
            .withDetail("qdrant", qdrantOk ? "UP" : "DOWN")
            .build();
    }

    private boolean checkNeo4j() {
        try (var session = neo4jDriver.session()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkQdrant() {
        try {
            String host = System.getenv().getOrDefault("QDRANT_HOST", "localhost");
            // QDRANT_PORT is the gRPC port; health check always hits the REST port 6333
            var url = URI.create("http://" + host + ":6333/readyz").toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
