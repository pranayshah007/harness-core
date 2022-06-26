package io.harness.cdng.infra.yaml;

import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InfraConfigStepParameter implements StepParameters {
  private String infraName;
  private String infraIdentifier;
  private Infrastructure spec;
}
