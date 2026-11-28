package dayum.aiagent.orchestrator.client.chat.anthropic;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties("anthropic")
public class AnthropicProperties {

  private String baseUrl;
  private String apiKey;
  private String version = "2023-06-01";
  private int maxTokens = 4096;
  private double temperature = 0.5;
}
