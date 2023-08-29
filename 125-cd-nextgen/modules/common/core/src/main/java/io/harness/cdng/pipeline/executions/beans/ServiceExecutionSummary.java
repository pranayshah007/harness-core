/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.manifest.yaml.summary.ManifestStoreInfo;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
@RecasterAlias("io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary")
@OwnedBy(HarnessTeam.CDP)
public class ServiceExecutionSummary {
  String identifier;
  String displayName;
  String deploymentType;
  boolean gitOpsEnabled;
  ArtifactsSummary artifacts;
  ManifestStoreInfo manifestInfo;

  @Data
  @Builder
  @RecasterAlias("io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary$ArtifactsSummary")
  public static class ArtifactsSummary {
    private ArtifactSummary primary;
    private String artifactDisplayName;
    @Singular private List<ArtifactSummary> sidecars;
  }
}
