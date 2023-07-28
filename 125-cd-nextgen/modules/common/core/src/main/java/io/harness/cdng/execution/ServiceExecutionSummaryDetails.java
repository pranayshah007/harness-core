/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.manifest.yaml.summary.ManifestSummary;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Data
@Builder
@RecasterAlias("io.harness.cdng.execution.ServiceExecutionSummaryDetails")
public class ServiceExecutionSummaryDetails {
  String identifier;
  String displayName;
  String deploymentType;
  boolean gitOpsEnabled;
  ArtifactsSummary artifacts;
  ManifestsSummary manifests;

  @Data
  @Builder
  @RecasterAlias("io.harness.cdng.execution.ServiceExecutionSummaryDetails$ArtifactsSummary")
  public static class ArtifactsSummary {
    private ArtifactSummary primary;
    private String artifactDisplayName;
    @Singular private List<ArtifactSummary> sidecars;
  }

  @Data
  @Builder
  @RecasterAlias("io.harness.cdng.execution.ServiceExecutionSummaryDetails$ManifestsSummary")
  public static class ManifestsSummary {
    @Singular private List<ManifestSummary> manifestSummaries;
  }
}
