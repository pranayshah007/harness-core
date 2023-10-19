/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.evaluators;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDP)
@Singleton
public class ProvisionerExpressionEvaluatorProvider {
  @Inject private ProvisionerOutputHelper provisionerOutputHelper;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;

  public ProvisionerExpressionEvaluator getProvisionerExpressionEvaluator(
      Ambiance ambiance, String provisionerIdentifier) {
    Preconditions.checkArgument(ambiance != null, "Ambiance cannot be null");
    Map<String, Object> provisionerOutput = isEmpty(provisionerIdentifier)
        ? Collections.emptyMap()
        : provisionerOutputHelper.getProvisionerOutputAsMap(ambiance, provisionerIdentifier);
    return new ProvisionerExpressionEvaluator(provisionerOutput, inputSetValidatorFactory);
  }
}
