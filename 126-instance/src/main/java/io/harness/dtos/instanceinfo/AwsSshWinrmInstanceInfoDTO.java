package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@EqualsAndHashCode(callSuper = true)
public class AwsSshWinrmInstanceInfoDTO extends SshWinrmInstanceInfoDTO {
  @Builder
  public AwsSshWinrmInstanceInfoDTO(String serviceType, String infrastructureKey, String host) {
    super(serviceType, infrastructureKey, host);
  }

  @Override
  public String getType() {
    return "AwsSshWinrm";
  }
}
