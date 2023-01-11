/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nonnull;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class InfrastructureOutcomeProvider {
  @Inject private InfrastructureProvisionerMapper infrastructureProvisionerMapper;
  @Inject private InfrastructureMapper infrastructureMapper;

  public InfrastructureOutcome getOutcome(@Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome,
      ServiceStepOutcome service, final String accountIdentifier, final String orgIdentifier,
      final String projectIdentifier) {
    if (InfrastructureKind.PDC.equals(infrastructure.getKind())
        && ((PdcInfrastructure) infrastructure).isDynamicallyProvisioned()) {
      return infrastructureProvisionerMapper.toOutcome(infrastructure, environmentOutcome, service);
    }

    return infrastructureMapper.toOutcome(
        infrastructure, environmentOutcome, service, accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
