package io.harness.delegate.beans.executioncapability;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsCliInstallationCapability implements ExecutionCapability {
  String criteria;
  CapabilityType capabilityType = CapabilityType.AWS_CLI_INSTALL;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return criteria;
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }
}
