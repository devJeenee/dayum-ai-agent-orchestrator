package dayum.aiagent.orchestrator.client.chat.clova.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dayum.aiagent.orchestrator.client.chat.ModelType;
import dayum.aiagent.orchestrator.client.chat.schema.SchemaFactory;
import java.util.List;

public sealed interface ChatCompletionRequest
    permits ChatCompletionHcx005Request, ChatCompletionHcx007Request {

  record Message(String role, List<Content> content) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image_url")
  })
  interface Content {}

  record TextContent(String type, String text) implements Content {}

  record ImageContent(String type, ImageUrl imageUrl) implements Content {}

  record ImageUrl(String url) {}

  record Thinking(String effort) {}

  static ChatCompletionRequest of(ModelType type, String systemMessage, String userMessage) {
    return switch (type) {
      case CLAUDE_HAIKU -> ChatCompletionHcx005Request.of(systemMessage, userMessage);
      case CLAUDE_SONNET -> ChatCompletionHcx007Request.of(systemMessage, userMessage);
    };
  }

  static ChatCompletionRequest forStructuredOutputs(
      ModelType type,
      String systemMessage,
      String userMessage,
      SchemaFactory.JsonSchema responseSchema) {
    return switch (type) {
      case CLAUDE_HAIKU -> throw new RuntimeException();
      case CLAUDE_SONNET ->
          ChatCompletionHcx007Request.of(systemMessage, userMessage, responseSchema);
    };
  }

  static ChatCompletionRequest ofImage(
      ModelType type, String systemMessage, String userMessage, String imageUrl) {
    return switch (type) {
      case CLAUDE_HAIKU -> ChatCompletionHcx005Request.ofImage(systemMessage, userMessage, imageUrl);
      case CLAUDE_SONNET -> throw new RuntimeException();
    };
  }
}
