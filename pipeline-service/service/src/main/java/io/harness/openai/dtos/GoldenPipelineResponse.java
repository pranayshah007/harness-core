package io.harness.openai.dtos;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoldenPipelineResponse {
  List<String> policyRecommendations;
}
