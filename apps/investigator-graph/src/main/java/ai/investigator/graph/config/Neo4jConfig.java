package ai.investigator.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "neo4j")
public class Neo4jConfig {

    private String uri;
    private Authentication authentication = new Authentication();

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public Authentication getAuthentication() { return authentication; }
    public void setAuthentication(Authentication authentication) { this.authentication = authentication; }

    public static class Authentication {
        private String username = "neo4j";
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
