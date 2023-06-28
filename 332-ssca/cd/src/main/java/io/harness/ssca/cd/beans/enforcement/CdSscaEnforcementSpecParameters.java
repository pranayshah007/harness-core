/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.beans.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.attestation.verify.VerifyAttestation;
import io.harness.ssca.beans.policy.EnforcementPolicy;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
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
public class CdSscaEnforcementSpecParameters
    extends CdSscaEnforcementBaseStepInfo implements SpecParameters, PluginStep {
  private String identifier;
  private String name;

  @Builder
  public CdSscaEnforcementSpecParameters(SbomSource source, VerifyAttestation verifyAttestation,
      EnforcementPolicy policy, ContainerStepInfra infrastructure, String name, String identifier) {
    super(source, verifyAttestation, policy, infrastructure);
    this.name = name;
    this.identifier = identifier;
  }

  @Override
  public ContainerStepType getType() {
    return ContainerStepType.CD_SSCA_ENFORCEMENT;
  }

  @Override
  public ParameterField<String> getConnectorRef() {
    if (source != null && SbomSourceType.IMAGE.equals(source.getType())) {
      return ((ImageSbomSource) source.getSbomSourceSpec()).getConnector();
    }
    return null;
  }
}
