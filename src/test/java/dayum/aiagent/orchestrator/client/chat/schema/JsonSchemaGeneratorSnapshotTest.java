package dayum.aiagent.orchestrator.client.chat.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dayum.aiagent.orchestrator.client.chat.dto.PlanningPlaybookResponse;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class JsonSchemaGeneratorSnapshotTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void planningPlaybookResponse_schema_matchesGolden() throws Exception {
    SchemaFactory.JsonSchema generated =
        JsonSchemaGenerator.generate(PlanningPlaybookResponse.class);

    JsonNode actual = objectMapper.valueToTree(generated);
    JsonNode expected;
    try (InputStream in =
        getClass().getClassLoader().getResourceAsStream("golden/planning-schema.json")) {
      expected = objectMapper.readTree(in);
    }

    assertThat(actual)
        .as(
            "Schema drift detected. If this change is intentional, update"
                + " src/test/resources/golden/planning-schema.json.\nActual:\n%s",
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated))
        .isEqualTo(expected);
  }
}
