package io.harness.cdng.infra.steps;

import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InfrastructureTaskExecutableStepV2Params implements StepParameters {
  private ParameterField<String> envRef;
  private ParameterField<String> infraRef;
  private ParameterField<Map<String, Object>> infraInputs;
}
