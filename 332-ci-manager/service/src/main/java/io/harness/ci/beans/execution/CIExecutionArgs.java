package io.harness.beans.execution;

import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CIExecutionArgs {
  Ambiance ambiance;
  StepElementParameters stepElementParameters;
  String callbackId;
}
