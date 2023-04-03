/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.evaluators.ProviderExpressionEvaluatorProvider;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nonnull;

@Singleton
@OwnedBy(CDP)
public class InfrastructureOutcomeProvider {
  @Inject private InfrastructureMapper infrastructureMapper;
  @Inject private ProviderExpressionEvaluatorProvider providerExpressionEvaluatorProvider;

  public InfrastructureOutcome getOutcome(Ambiance ambiance, @Nonnull Infrastructure infrastructure,
      EnvironmentOutcome environmentOutcome, ServiceStepOutcome service, final String accountIdentifier,
      final String orgIdentifier, final String projectIdentifier) {
    ProvisionerExpressionEvaluator expressionEvaluator =
        providerExpressionEvaluatorProvider.getProviderExpressionEvaluator(
            ambiance, infrastructure.getProvisionerStepIdentifier());
    return infrastructureMapper.toOutcome(infrastructure, expressionEvaluator, environmentOutcome, service,
        accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
