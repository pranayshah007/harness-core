/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.beans.stepinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.Attestation;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.tools.SbomOrchestrationTool;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias(SscaConstants.CD_SSCA_ORCHESTRATION_STEP_NODE)
@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationBaseStepInfo {
  @NotNull SbomOrchestrationTool tool;

  @NotNull SbomSource source;

  @NotNull Attestation attestation;

  @NotNull @Valid ContainerStepInfra infrastructure;
}
