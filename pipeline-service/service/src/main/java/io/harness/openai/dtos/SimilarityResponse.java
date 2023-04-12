package io.harness.openai.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SimilarityResponse {
  Integer pipelineSimilarityPercentage;
  List<String> response;
}
