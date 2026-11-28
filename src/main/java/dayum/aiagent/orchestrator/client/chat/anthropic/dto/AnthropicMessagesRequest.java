package dayum.aiagent.orchestrator.client.chat.anthropic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dayum.aiagent.orchestrator.client.chat.schema.SchemaFactory.JsonSchema;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnthropicMessagesRequest(
    String model,
    int max_tokens,
    Double temperature,
    String system,
    List<Message> messages,
    List<Tool> tools,
    Map<String, Object> tool_choice) {

  public record Message(String role, List<ContentBlock> content) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ContentBlock(String type, String text, ImageSource source) {
    public static ContentBlock text(String text) {
      return new ContentBlock("text", text, null);
    }

    public static ContentBlock imageUrl(String url) {
      return new ContentBlock("image", null, new ImageSource("url", url, null, null));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ImageSource(String type, String url, String media_type, String data) {}

  public record Tool(String name, String description, JsonSchema input_schema) {}
}
