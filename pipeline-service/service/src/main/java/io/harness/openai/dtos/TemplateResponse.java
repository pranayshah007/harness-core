package io.harness.openai.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TemplateResponse {
  List<String> templates;
  String pipelineYaml1;
  String pipelineYaml2;

}
