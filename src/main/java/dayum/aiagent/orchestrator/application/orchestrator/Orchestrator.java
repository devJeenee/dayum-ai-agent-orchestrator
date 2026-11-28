package dayum.aiagent.orchestrator.application.orchestrator;

import dayum.aiagent.orchestrator.application.context.model.ContextType;
import dayum.aiagent.orchestrator.application.context.model.ContextValue;
import dayum.aiagent.orchestrator.application.context.model.ConversationContext;
import dayum.aiagent.orchestrator.application.orchestrator.model.FailedStep;
import dayum.aiagent.orchestrator.application.orchestrator.model.PlaybookPlanResult;
import dayum.aiagent.orchestrator.application.orchestrator.model.PlaybookResult;
import dayum.aiagent.orchestrator.application.orchestrator.model.ReplanReason;
import dayum.aiagent.orchestrator.application.orchestrator.playbook.PlaybookType;
import dayum.aiagent.orchestrator.common.vo.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Orchestrator {

  static final int MAX_REPLAN = 2;

  private final PlaybookPlanner planner;

  public List<PlaybookResult> runTurn(ConversationContext context, UserMessage userMessage) {
    List<PlaybookResult> results = new ArrayList<>();
    List<PlaybookPlanResult> plans = planner.planning(context, userMessage);
    log.info("✅ Initial plans: {}", plans);

    int replanCount = 0;
    while (true) {
      FailedStep failure = executePlans(plans, context, userMessage, results);
      if (failure == null) {
        return results;
      }
      if (replanCount >= MAX_REPLAN) {
        results.add(createMissingContextResult(failure.playbookType(), failure.missingContexts()));
        return results;
      }
      replanCount++;
      plans = planner.replan(context, userMessage, failure);
      log.info("♻️ Replan #{} (failed={}): {}", replanCount, failure, plans);
    }
  }

  /** Executes plans until completion or the first missing-context failure. */
  private FailedStep executePlans(
      List<PlaybookPlanResult> plans,
      ConversationContext context,
      UserMessage userMessage,
      List<PlaybookResult> results) {
    for (PlaybookPlanResult plan : plans) {
      List<ContextType> missing =
          findMissingContexts(plan.playbook().getRequiresContext(), context.contexts());
      if (!missing.isEmpty()) {
        return new FailedStep(plan.playbook().getType(), ReplanReason.MISSING_CONTEXT, missing);
      }
      PlaybookResult result = plan.playbook().play(plan.reason(), context, userMessage);
      results.add(result);
      context.merge(result.output());
    }
    return null;
  }

  /** 현재 컨텍스트에 없는 필수 컨텍스트 찾기 */
  private List<ContextType> findMissingContexts(
      List<ContextType> requiredContexts, Map<ContextType, ContextValue> currentContexts) {

    if (requiredContexts == null || requiredContexts.isEmpty()) {
      return List.of();
    }

    return requiredContexts.stream()
        .filter(contextType -> !currentContexts.containsKey(contextType))
        .toList();
  }

  /** 필수 컨텍스트 부족 응답 생성 (초안) */
  private PlaybookResult createMissingContextResult(
      PlaybookType playbookType, List<ContextType> missingContexts) {

    StringBuilder message = new StringBuilder();
    message.append("필요한 정보가 부족해요 😅\n\n");

    for (ContextType missing : missingContexts) {
      if (missing == ContextType.PANTRY) {
        message.append("🥬 보유하신 재료를 먼저 알려주세요.\n");
      } else if (missing == ContextType.TASTE_ATTRIBUTE) {
        message.append("😋 음식 취향이나 알러지 정보를 알려주세요.\n");
      }
    }
    return new PlaybookResult("정보 부족", message.toString(), List.of(), Map.of());
  }
}
