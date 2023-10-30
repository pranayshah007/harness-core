/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dtos.rollback;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public class NativeHelmPostProdRollbackInfo implements PostProdRollbackSwimLaneInfo {
  private final String lastPipelineExecutionName;
  private final String lastPipelineExecutionId;
  private final long lastDeployedAt;
  private final String envName;
  private final String envIdentifier;
  private final String infraName;
  private final String infraIdentifier;
  private final String currentArtifactDisplayName;
  private final String currentArtifactId;
  private final String previousArtifactDisplayName;
  private final String previousArtifactId;
}
