/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import javax.annotation.Nonnull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@OwnedBy(CDP)
public class InfrastructureOutcomeProvider {
  @Inject private InfrastructureMapper infrastructureMapper;

  public InfrastructureOutcome getOutcome(Ambiance ambiance, @Nonnull Infrastructure infrastructure,
      EnvironmentOutcome environmentOutcome, ServiceStepOutcome service, final String accountIdentifier,
      final String orgIdentifier, final String projectIdentifier, Map<String, String> tags) {
    return infrastructureMapper.toOutcome(infrastructure, ambiance, environmentOutcome, service, accountIdentifier,
        orgIdentifier, projectIdentifier, tags);
  }
}
