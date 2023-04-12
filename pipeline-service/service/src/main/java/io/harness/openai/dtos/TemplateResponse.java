package io.harness.openai.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateResponse {
  String templateYaml;
  String pipelineYaml1;
  String pipelineYaml2;

}
