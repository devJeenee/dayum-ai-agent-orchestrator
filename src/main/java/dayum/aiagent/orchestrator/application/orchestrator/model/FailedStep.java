package dayum.aiagent.orchestrator.application.orchestrator.model;

import dayum.aiagent.orchestrator.application.context.model.ContextType;
import dayum.aiagent.orchestrator.application.orchestrator.playbook.PlaybookType;
import java.util.List;

public record FailedStep(
    PlaybookType playbookType, ReplanReason reason, List<ContextType> missingContexts) {}
