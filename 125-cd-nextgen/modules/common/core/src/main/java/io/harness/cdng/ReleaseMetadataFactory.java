/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class ReleaseMetadataFactory {
  @Inject private CDStepHelper cdStepHelper;
  public ReleaseMetadata createReleaseMetadata(InfrastructureOutcome infraOutcome, Ambiance ambiance) {
    try {
      String serviceId = getServiceIdentifier(ambiance).orElse(null);
      String infraId = infraOutcome.getInfraIdentifier();
      String infraKey = infraOutcome.getInfrastructureKey();
      String envId = getEnvironmentIdentifier(infraOutcome).orElse(null);
      return ReleaseMetadata.builder().serviceId(serviceId).infraId(infraId).infraKey(infraKey).envId(envId).build();
    } catch (Exception e) {
      log.warn("Failed to create k8s release metadata. ", e);
      return null;
    }
  }

  private Optional<String> getEnvironmentIdentifier(InfrastructureOutcome infraOutcome) {
    if (infraOutcome.getEnvironment() != null) {
      return Optional.ofNullable(infraOutcome.getEnvironment().getIdentifier());
    }
    return Optional.empty();
  }

  private Optional<String> getServiceIdentifier(Ambiance ambiance) {
    ServiceStepOutcome serviceStepOutcome = cdStepHelper.getServiceStepOutcome(ambiance);
    if (serviceStepOutcome != null) {
      return Optional.ofNullable(serviceStepOutcome.getIdentifier());
    }
    return Optional.empty();
  }
}
