package ai.investigator.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";
    private String modelId = "qwen3.6:35b";
    private double temperature = 0.6;
    private int numCtx = 32768;
    private int numPredict = 4096;
    private long timeoutSeconds = 600;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getNumCtx() { return numCtx; }
    public void setNumCtx(int numCtx) { this.numCtx = numCtx; }

    public int getNumPredict() { return numPredict; }
    public void setNumPredict(int numPredict) { this.numPredict = numPredict; }

    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
