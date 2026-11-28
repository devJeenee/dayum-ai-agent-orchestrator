package dayum.aiagent.orchestrator.client.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelType {
  CLAUDE_SONNET("claude-sonnet-4"),
  CLAUDE_HAIKU("claude-haiku-4");

  private final String name;
}
