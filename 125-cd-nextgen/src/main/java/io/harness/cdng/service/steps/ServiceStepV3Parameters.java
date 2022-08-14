package io.harness.cdng.service.steps;

import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceStepV3Parameters implements StepParameters {
  private ParameterField<String> serviceRef;
  private Map<String, Object> inputs;
}
