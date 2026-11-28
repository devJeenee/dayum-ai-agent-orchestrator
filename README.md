# Dayum AI Agent Orchestrator

Spring Boot 기반 음식 도메인 LLM 에이전트 오케스트레이터. **Playbook sub-agent 8종**을 LLM Planner 가 조합해 멀티스텝 플랜으로 실행한다.

- **런타임:** Java 21, Spring Boot 3.5
- **LLM:** Anthropic Claude Sonnet / Haiku (프록시 호환) · Clova HCX-005/007 호환 유지 (`chat.provider=clova`)
- **규모:** 77 Java files · 3,920 LOC · 20 merged PRs
- 자세한 설계: [`ARCHITECTURE.md`](./ARCHITECTURE.md)

---

## Highlights

1. **Playbook sub-agent 아키텍처** — 8개 sub-agent 가 self-describing capability card 를 선언, Spring `List<Playbook>` 자동 수집으로 plug-and-play. flat tool-router → sub-agent 로 PR #14 단독 +401 / -509 lines 교체. → [§1](./ARCHITECTURE.md#1-playbook-패턴-sub-agent-아키텍처)
2. **Reflective JSON Schema (103 LOC)** — 응답 DTO 로부터 Clova/Anthropic structured-output 스키마 자동 생성, sealed `JsonSchema` 4 variants 로 Jackson 타입 안전 확보. → [§3](./ARCHITECTURE.md#3-reflective-json-schema-103-loc)
3. **2단 대화 메모리** — sealed `ContextValue` 3종 (pantry / taste / recommended-recipe) per-type merge + 300-800자 LLM rolling summary. → [§4](./ARCHITECTURE.md#4-2단-대화-메모리)
4. **Self-correcting replan loop** — precondition 실패를 신호로 LLM 재계획 (cap=2). `OrchestratorReplanTest` 로 복구 + cap 검증. → [§5](./ARCHITECTURE.md#5-self-correcting-replan-loop)

---

## Design decisions & tradeoffs

- **In-memory repositories 는 의도된 데모 범위.** `DomainContextInMemoryRepository` / `RollingSummaryInMemoryRepository` 는 `ContextRepository` 빈 1개 교체로 Redis 로 이관 가능. 영속화 자체보다 **Playbook / schema generator / replan loop** 설계가 보이는 것을 우선.
- **LLM 공급자는 교체 가능.** `ChatClient` 인터페이스 뒤에 `AnthropicChatClient` (`chat.provider=anthropic`, 기본) 와 `ClovaStudioChatClient` (`chat.provider=clova`) 가 `@ConditionalOnProperty` 로 공존. 모델 식별자는 provider-agnostic 한 `ModelType.CLAUDE_SONNET` / `CLAUDE_HAIKU` 로 통일.
- **Tool-calling 재도입하지 않음.** PR #14 에서 flat tool-router 를 의도적으로 걷어낸 결과물이므로, 복원은 네러티브와 상충.
- **WebSocket 은 stub.** REST 경로로 end-to-end 동작 충분. 세션/백프레셔는 별도 작업.

---

## What's next

- **Redis-backed memory** — sealed `ContextValue` + Jackson 다형성 (`@JsonTypeInfo`) 라운드트립
- **Micrometer 슬라이스** — `ClovaStudioChatClient.sendRequest` 래핑으로 `llm_tokens_input_total{model, playbook}` 노출 (tag cardinality 주의: userId 금지)
- **Replan 관측성** — `replanCount` 메트릭 + 실패 플랜/성공 플랜 비교 로그

---

## Build & run

```bash
# 환경변수
export ANTHROPIC_API_KEY=...        # 필수 (기본 provider=anthropic)
# export ANTHROPIC_BASE_URL=...     # 기본 https://copa.codyssey.kr
# export CHAT_PROVIDER=clova        # Clova 로 전환 시

./gradlew compileJava               # 컴파일 확인
./gradlew test --tests "dayum.aiagent.orchestrator.application.orchestrator.*" \
               --tests "dayum.aiagent.orchestrator.client.chat.schema.*"
./gradlew bootRun                   # local 프로파일 — AWS Secrets Manager 접근 필요
```

> **참고:** `DayumAiAgentOrchestratorApplicationTests.contextLoads()` 는 AWS Secrets Manager (`aws-secretsmanager:dayum/server/dev`) 접근을 요구하므로 로컬 전체 테스트 실행 시 실패할 수 있다. 결정적 단위 테스트는 위 `--tests` 필터로 실행.
