package io.harness.cdng.gitops.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class DeleteInstancesRequest {
  boolean status;
  int deletedCount;
}
