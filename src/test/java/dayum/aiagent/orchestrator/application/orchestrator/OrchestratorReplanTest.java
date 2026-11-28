package dayum.aiagent.orchestrator.application.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dayum.aiagent.orchestrator.application.context.model.ContextType;
import dayum.aiagent.orchestrator.application.context.model.ContextValue;
import dayum.aiagent.orchestrator.application.context.model.ConversationContext;
import dayum.aiagent.orchestrator.application.context.model.PantryContext;
import dayum.aiagent.orchestrator.application.orchestrator.model.FailedStep;
import dayum.aiagent.orchestrator.application.orchestrator.model.PlaybookPlanResult;
import dayum.aiagent.orchestrator.application.orchestrator.model.PlaybookResult;
import dayum.aiagent.orchestrator.application.orchestrator.playbook.Playbook;
import dayum.aiagent.orchestrator.application.orchestrator.playbook.PlaybookType;
import dayum.aiagent.orchestrator.common.vo.UserMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrchestratorReplanTest {

  private PlaybookPlanner planner;
  private Orchestrator orchestrator;
  private Playbook recommendDiet;
  private Playbook rememberIngredient;

  @BeforeEach
  void setUp() {
    planner = mock(PlaybookPlanner.class);
    orchestrator = new Orchestrator(planner);

    recommendDiet = mock(Playbook.class);
    when(recommendDiet.getType()).thenReturn(PlaybookType.RECOMMEND_DIET_RECIPE);
    when(recommendDiet.getRequiresContext()).thenReturn(List.of(ContextType.PANTRY));
    when(recommendDiet.play(any(), any(), any()))
        .thenReturn(new PlaybookResult("추천", "추천 결과", List.of(), Map.of()));

    rememberIngredient = mock(Playbook.class);
    when(rememberIngredient.getType()).thenReturn(PlaybookType.REMEMBER_INGREDIENT);
    when(rememberIngredient.getRequiresContext()).thenReturn(List.of());
    when(rememberIngredient.play(any(), any(), any()))
        .thenAnswer(
            inv ->
                new PlaybookResult(
                    "기억",
                    "재료 저장",
                    List.of(),
                    Map.of(ContextType.PANTRY, new PantryContext(List.of()))));
  }

  @Test
  void replan_recoversFromMissingContext() {
    UserMessage userMessage = new UserMessage("양파로 저녁 추천", null, null);
    ConversationContext context = emptyContext();

    var initialPlans = List.of(planResult(recommendDiet));
    var replanPlans = List.of(planResult(rememberIngredient), planResult(recommendDiet));

    // Initial plan: RECOMMEND_DIET_RECIPE only — will fail because PANTRY missing.
    doReturn(initialPlans).when(planner).planning(eq(context), eq(userMessage));
    // After replan: REMEMBER_INGREDIENT first (adds PANTRY), then RECOMMEND_DIET_RECIPE.
    doReturn(replanPlans)
        .when(planner)
        .replan(eq(context), eq(userMessage), any(FailedStep.class));

    List<PlaybookResult> results = orchestrator.runTurn(context, userMessage);

    // Both playbooks ran; no "정보 부족" error.
    assertThat(results).hasSize(2);
    assertThat(results.get(0).title()).isEqualTo("기억");
    assertThat(results.get(1).title()).isEqualTo("추천");
    assertThat(results).noneMatch(r -> r.title().equals("정보 부족"));

    verify(planner, times(1)).planning(any(), any());
    verify(planner, times(1)).replan(any(), any(), any());
  }

  @Test
  void replan_cappedAtMaxReplan_whenPlannerKeepsFailing() {
    UserMessage userMessage = new UserMessage("저녁 추천", null, null);
    ConversationContext context = emptyContext();

    var failingPlans = List.of(planResult(recommendDiet));

    // Both initial plan and every replan return the same missing-context plan.
    doReturn(failingPlans).when(planner).planning(eq(context), eq(userMessage));
    doReturn(failingPlans)
        .when(planner)
        .replan(eq(context), eq(userMessage), any(FailedStep.class));

    List<PlaybookResult> results = orchestrator.runTurn(context, userMessage);

    // Exactly MAX_REPLAN replans attempted; final result contains the missing-context error.
    verify(planner, times(1)).planning(any(), any());
    verify(planner, times(Orchestrator.MAX_REPLAN)).replan(any(), any(), any());
    assertThat(results).isNotEmpty();
    assertThat(results.get(results.size() - 1).title()).isEqualTo("정보 부족");
    // Recommend playbook must never have executed.
    verify(recommendDiet, never()).play(any(), any(), any());
  }

  private static PlaybookPlanResult planResult(Playbook playbook) {
    return new PlaybookPlanResult(playbook.getType(), playbook, "test-reason");
  }

  private static ConversationContext emptyContext() {
    return ConversationContext.builder()
        .memberId(1L)
        .sessionId("test-session")
        .contexts(new HashMap<ContextType, ContextValue>())
        .shortTermContexts(List.of())
        .rollingSummary("")
        .build();
  }
}
