package io.harness.delegate.task.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.pcf.response.TasInfraConfig;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class TasDeploymentReleaseData {
  private TasInfraConfig tasInfraConfig;
  private String applicationName;
}
