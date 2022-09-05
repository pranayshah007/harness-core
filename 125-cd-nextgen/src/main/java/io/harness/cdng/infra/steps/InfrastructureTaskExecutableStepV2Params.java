package io.harness.cdng.infra.steps;

import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InfrastructureTaskExecutableStepV2Params implements StepParameters {
  @NotNull private ParameterField<String> envRef;
  @NotNull private ParameterField<String> infraRef;
  private Map<String, Object> infraInputs;
}
