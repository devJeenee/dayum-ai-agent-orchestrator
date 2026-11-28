package dayum.aiagent.orchestrator.client.chat.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import dayum.aiagent.orchestrator.application.context.model.ConversationContext;
import dayum.aiagent.orchestrator.client.chat.ChatClient;
import dayum.aiagent.orchestrator.client.chat.ChatPrompt;
import dayum.aiagent.orchestrator.client.chat.ModelType;
import dayum.aiagent.orchestrator.client.chat.anthropic.dto.AnthropicMessagesRequest;
import dayum.aiagent.orchestrator.client.chat.anthropic.dto.AnthropicMessagesResponse;
import dayum.aiagent.orchestrator.client.chat.dto.ChatCompletionResponse;
import dayum.aiagent.orchestrator.client.chat.schema.SchemaFactory.*;
import io.vavr.control.Try;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicChatClient implements ChatClient {

  private static final String STRUCTURED_TOOL_NAME = "respond_with_structured_output";

  private final Handlebars handlebars = new Handlebars();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnthropicProperties properties;
  private final RestClient restClient;

  @Override
  public ChatCompletionResponse chatCompletion(
      String systemMessage, String userMessage, ModelType modelType) {
    var request = buildRequest(modelType, systemMessage, userMessage, null, null);
    return convert(sendRequest(request));
  }

  @Override
  public ChatCompletionResponse chatCompletionWithImage(
      String systemMessage, String userMessage, String imageUrl, ModelType modelType) {
    var userContent =
        List.of(
            AnthropicMessagesRequest.ContentBlock.imageUrl(imageUrl),
            AnthropicMessagesRequest.ContentBlock.text(userMessage));
    var request =
        new AnthropicMessagesRequest(
            modelType.getName(),
            properties.getMaxTokens(),
            properties.getTemperature(),
            systemMessage,
            List.of(new AnthropicMessagesRequest.Message("user", userContent)),
            null,
            null);
    return convert(sendRequest(request));
  }

  @Override
  public ChatCompletionResponse chatCompletion(
      String systemMessage, String userMessage, ConversationContext context, ModelType modelType) {
    String merged = renderContext(context) + userMessage;
    return chatCompletion(systemMessage, merged, modelType);
  }

  @Override
  public ChatCompletionResponse chatCompletion(
      String systemMessage,
      String userMessage,
      String context,
      List<ToolSchema> toolSchemas,
      ModelType modelType) {
    // Not implemented — Playbook architecture (PR #14) removed the flat tool-router path.
    return null;
  }

  @Override
  public ChatCompletionResponse chatCompletionWithStructuredOutput(
      String systemMessage, String userMessage, JsonSchema outputSchema, ModelType modelType) {
    var tool =
        new AnthropicMessagesRequest.Tool(
            STRUCTURED_TOOL_NAME, "Return the response in the required JSON schema.", outputSchema);
    var toolChoice = Map.<String, Object>of("type", "tool", "name", STRUCTURED_TOOL_NAME);
    var request =
        buildRequest(modelType, systemMessage, userMessage, List.of(tool), toolChoice);
    return convert(sendRequest(request));
  }

  @Override
  public ChatCompletionResponse chatCompletionWithStructuredOutput(
      String systemMessage,
      String userMessage,
      ConversationContext context,
      JsonSchema outputSchema,
      ModelType modelType) {
    String merged = renderContext(context) + userMessage;
    return chatCompletionWithStructuredOutput(systemMessage, merged, outputSchema, modelType);
  }

  private AnthropicMessagesRequest buildRequest(
      ModelType modelType,
      String systemMessage,
      String userMessage,
      List<AnthropicMessagesRequest.Tool> tools,
      Map<String, Object> toolChoice) {
    return new AnthropicMessagesRequest(
        modelType.getName(),
        properties.getMaxTokens(),
        properties.getTemperature(),
        systemMessage,
        List.of(
            new AnthropicMessagesRequest.Message(
                "user", List.of(AnthropicMessagesRequest.ContentBlock.text(userMessage)))),
        tools,
        toolChoice);
  }

  private String renderContext(ConversationContext context) {
    try {
      return handlebars
          .compileInline(ChatPrompt.CONTEXT_MESSAGE)
          .apply(
              new HashMap<>() {
                {
                  this.put("rollingSummary", context.rollingSummary());
                  this.put("shortTermContext", context.shortTermContexts());
                  this.put(
                      "currentContextKey",
                      objectMapper.writeValueAsString(context.contexts().keySet()));
                }
              });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private AnthropicMessagesResponse sendRequest(AnthropicMessagesRequest request) {
    return Try.ofCallable(
            () ->
                restClient
                    .post()
                    .uri(properties.getBaseUrl() + "/v1/messages")
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", properties.getVersion())
                    .body(request)
                    .retrieve()
                    .body(AnthropicMessagesResponse.class))
        .getOrElseThrow(ex -> new RuntimeException(ex));
  }

  private ChatCompletionResponse convert(AnthropicMessagesResponse response) {
    log.info("Anthropic response: {}", response);
    String text = "";
    String structuredJson = null;
    for (var block : response.content()) {
      if ("text".equals(block.type()) && block.text() != null) {
        text = text.isEmpty() ? block.text() : text + "\n" + block.text();
      } else if ("tool_use".equals(block.type()) && block.input() != null) {
        try {
          structuredJson = objectMapper.writeValueAsString(block.input());
        } catch (Exception e) {
          throw new RuntimeException("Failed to serialize tool_use input", e);
        }
      }
    }
    String message = structuredJson != null ? structuredJson : stripCodeFence(text);
    return new ChatCompletionResponse(
        response.role(), message, List.of(), mapStopReason(response.stop_reason()));
  }

  private String stripCodeFence(String text) {
    return text.replaceAll("```json", "").replaceAll("```", "").trim();
  }

  private String mapStopReason(String anthropicStopReason) {
    if (anthropicStopReason == null) return null;
    return switch (anthropicStopReason) {
      case "end_turn", "stop_sequence" -> "stop";
      case "max_tokens" -> "length";
      case "tool_use" -> "stop";
      default -> anthropicStopReason;
    };
  }
}
