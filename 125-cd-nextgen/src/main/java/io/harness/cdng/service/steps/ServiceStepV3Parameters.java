package io.harness.cdng.service.steps;

import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceStepV3Parameters implements StepParameters {
  private ParameterField<String> serviceRef;
  private ParameterField<Map<String, Object>> inputs;
  private ParameterField<String> envRef;
  private ParameterField<Map<String, Object>> envInputs;

  private List<String> childrenNodeIds;
}
