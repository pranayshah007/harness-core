/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.beans.stepinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.ssca.beans.Attestation;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.tools.SbomOrchestrationTool;
import io.harness.steps.plugin.ContainerStepType;
import io.harness.steps.plugin.PluginStep;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationSpecParameters
    extends CdSscaOrchestrationBaseStepInfo implements SpecParameters, PluginStep {
  private String identifier;
  private String name;

  public ContainerStepType getType() {
    return ContainerStepType.CD_SSCA_ORCHESTRATION;
  }

  @Builder
  public CdSscaOrchestrationSpecParameters(SbomOrchestrationTool tool, SbomSource source, Attestation attestation,
      ContainerStepInfra infrastructure, String name, String identifier) {
    super(tool, source, attestation, infrastructure);
    this.name = name;
    this.identifier = identifier;
  }
}
