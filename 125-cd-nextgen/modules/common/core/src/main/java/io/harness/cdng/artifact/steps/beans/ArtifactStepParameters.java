/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps.beans;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("artifactStepParameters")
@RecasterAlias("io.harness.cdng.artifact.steps.ArtifactStepParameters")
public class ArtifactStepParameters implements StepParameters {
  String identifier;
  ArtifactSourceType type;
  ArtifactConfig spec;
  ArtifactConfig stageOverride;
}
