package io.harness.delegate.task.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class PcfDeploymentReleaseData {
  private PcfInfraConfig pcfInfraConfig;
  private String applicationName;
}
