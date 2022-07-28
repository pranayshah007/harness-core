package io.harness.steps.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyInfo {
  int maxConcurrency;
  List<JsonNode> expandedJsonNodes;
}
