package ai.investigator.vector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
public class QdrantConfig {

    private String host;
    private int port = 6333;
    private String defaultCollection = "documents";
    private String embeddingModel = "nomic-embed-text";
    private String ollamaBaseUrl = "http://localhost:11434";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDefaultCollection() { return defaultCollection; }
    public void setDefaultCollection(String defaultCollection) { this.defaultCollection = defaultCollection; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    public void setOllamaBaseUrl(String ollamaBaseUrl) { this.ollamaBaseUrl = ollamaBaseUrl; }
}
