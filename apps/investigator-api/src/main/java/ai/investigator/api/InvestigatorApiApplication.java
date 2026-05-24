package ai.investigator.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "ai.investigator")
@ConfigurationPropertiesScan(basePackages = "ai.investigator")
public class InvestigatorApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestigatorApiApplication.class, args);
    }
}
