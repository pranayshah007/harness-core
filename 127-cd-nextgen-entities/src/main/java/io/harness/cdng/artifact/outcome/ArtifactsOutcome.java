/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(
    module = ProductModule.CDS, components = {HarnessModuleComponent.CDS_ARTIFACTS}, unitCoverageRequired = false)
@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@JsonTypeName("ArtifactsOutcome")
@TypeAlias("artifactsOutcome")
@RecasterAlias("io.harness.ngpipeline.artifact.bean.ArtifactsOutcome")
public class ArtifactsOutcome implements Outcome, ExecutionSweepingOutput {
  ArtifactOutcome primary;
  SidecarsOutcome sidecars;
}
