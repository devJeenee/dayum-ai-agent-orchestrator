package dayum.aiagent.orchestrator.client.chat.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicMessagesResponse(
    String id,
    String type,
    String role,
    String model,
    List<ContentBlock> content,
    String stop_reason,
    String stop_sequence,
    Usage usage) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ContentBlock(
      String type, String text, String id, String name, Map<String, Object> input) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Usage(Integer input_tokens, Integer output_tokens) {}
}
