package io.harness.artifacts.gar.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDC)
public class GarInternalConfig {
  String bearerToken;
  String project;
  String repositoryName;
  String region;
  String pkg;
  int maxBuilds;
  boolean isCertValidationRequired;
}
