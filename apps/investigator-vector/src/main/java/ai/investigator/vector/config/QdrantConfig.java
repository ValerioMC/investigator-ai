package ai.investigator.vector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
public class QdrantConfig {

    private String host;
    private int port = 6333;
    private String defaultCollection = "documents";
    private String embeddingModel = "mlx-community/nomicai-modernbert-embed-base-bf16";
    private String embeddingBaseUrl = "http://host.minikube.internal:8082/v1";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDefaultCollection() { return defaultCollection; }
    public void setDefaultCollection(String defaultCollection) { this.defaultCollection = defaultCollection; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
    public void setEmbeddingBaseUrl(String embeddingBaseUrl) { this.embeddingBaseUrl = embeddingBaseUrl; }
}
