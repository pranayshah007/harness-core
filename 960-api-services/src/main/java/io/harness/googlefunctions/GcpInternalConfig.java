package io.harness.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDC)
public class GcpInternalConfig {
  char[] serviceAccountKeyFileContent;
  boolean isUseDelegate;
  String project;
  String region;
}