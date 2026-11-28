package dayum.aiagent.orchestrator.application.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dayum.aiagent.orchestrator.application.context.model.ConversationContext;
import dayum.aiagent.orchestrator.application.orchestrator.model.PlaybookCatalog;
import dayum.aiagent.orchestrator.application.orchestrator.model.PlaybookPlanResult;
import dayum.aiagent.orchestrator.application.orchestrator.playbook.Playbook;
import dayum.aiagent.orchestrator.application.orchestrator.playbook.PlaybookType;
import dayum.aiagent.orchestrator.client.chat.ChatClientService;
import dayum.aiagent.orchestrator.client.chat.dto.PlanningPlaybookResponse;
import dayum.aiagent.orchestrator.common.enums.QuickReply;
import dayum.aiagent.orchestrator.common.vo.UserMessage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaybookPlannerTest {

  private ChatClientService chatClientService;
  private PlaybookPlanner planner;

  @BeforeEach
  void setUp() {
    chatClientService = mock(ChatClientService.class);
    List<Playbook> playbooks =
        Arrays.stream(PlaybookType.values()).map(PlaybookPlannerTest::stubPlaybook).toList();
    planner = new PlaybookPlanner(playbooks, chatClientService);
  }

  @Test
  void planning_sortsStepsByPriority() {
    UserMessage userMessage = new UserMessage("오늘 뭐 먹지", null, null);
    ConversationContext context = emptyContext();

    PlanningPlaybookResponse canned =
        new PlanningPlaybookResponse(
            List.of(
                new PlanningPlaybookResponse.Step(
                    PlaybookType.RECOMMEND_DIET_RECIPE.name(), "추천 먼저", 2),
                new PlanningPlaybookResponse.Step(
                    PlaybookType.REMEMBER_INGREDIENT.name(), "재료 저장", 1)));
    when(chatClientService.planningSteps(eq(context), eq(userMessage), any(), isNull()))
        .thenReturn(canned);

    List<PlaybookPlanResult> result = planner.planning(context, userMessage);

    assertThat(result)
        .extracting(PlaybookPlanResult::type)
        .containsExactly(PlaybookType.REMEMBER_INGREDIENT, PlaybookType.RECOMMEND_DIET_RECIPE);
  }

  @Test
  void planning_fallsBackToGuardrail_whenPlannerReturnsEmpty() {
    UserMessage userMessage = new UserMessage("...", null, null);
    ConversationContext context = emptyContext();

    when(chatClientService.planningSteps(eq(context), eq(userMessage), any(), isNull()))
        .thenReturn(new PlanningPlaybookResponse(List.of()));

    List<PlaybookPlanResult> result = planner.planning(context, userMessage);

    assertThat(result).extracting(PlaybookPlanResult::type).containsExactly(PlaybookType.GUARDRAIL);
  }

  @Test
  void planning_routesPickThisQuickReplyToSmallTalk_withoutCallingPlanner() {
    UserMessage userMessage = new UserMessage("", QuickReply.PICK_THIS, null);
    ConversationContext context = emptyContext();

    List<PlaybookPlanResult> result = planner.planning(context, userMessage);

    assertThat(result)
        .extracting(PlaybookPlanResult::type)
        .containsExactly(PlaybookType.SMALL_TALK);
  }

  @Test
  void planning_routesFindAlternativesQuickReplyToRecommend() {
    UserMessage userMessage = new UserMessage("", QuickReply.FIND_ALTERNATIVES, null);
    ConversationContext context = emptyContext();

    List<PlaybookPlanResult> result = planner.planning(context, userMessage);

    assertThat(result)
        .extracting(PlaybookPlanResult::type)
        .containsExactly(PlaybookType.RECOMMEND_DIET_RECIPE);
  }

  @Test
  void planning_recommendDietRecipe_forFoodQuestion() {
    UserMessage userMessage = new UserMessage("저녁 메뉴 추천해줘", null, null);
    ConversationContext context = emptyContext();

    when(chatClientService.planningSteps(eq(context), eq(userMessage), any(), isNull()))
        .thenReturn(
            new PlanningPlaybookResponse(
                List.of(
                    new PlanningPlaybookResponse.Step(
                        PlaybookType.RECOMMEND_DIET_RECIPE.name(), "식사 추천", 1))));

    List<PlaybookPlanResult> result = planner.planning(context, userMessage);

    assertThat(result)
        .extracting(PlaybookPlanResult::type)
        .containsExactly(PlaybookType.RECOMMEND_DIET_RECIPE);
  }

  private static ConversationContext emptyContext() {
    return ConversationContext.builder()
        .memberId(1L)
        .sessionId("test-session")
        .contexts(new java.util.HashMap<>())
        .shortTermContexts(List.of())
        .rollingSummary("")
        .build();
  }

  private static Playbook stubPlaybook(PlaybookType type) {
    Playbook playbook = mock(Playbook.class);
    when(playbook.getType()).thenReturn(type);
    when(playbook.getCatalog())
        .thenReturn(
            PlaybookCatalog.builder()
                .id(type.name())
                .action("test-action")
                .trigger(List.of())
                .requiresContext(List.of())
                .outputContext(List.of())
                .cautions(List.of())
                .build());
    return playbook;
  }
}
