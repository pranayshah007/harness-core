/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;

import lombok.NonNull;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public class EnvGlobalOverrideRequestParamsHandler implements ServiceOverrideTypeBasedRequestParamsHandler {
  @Override
  public void validateRequest(@NonNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId) {}

  @Override
  public String generateServiceOverrideIdentifier(@NonNull NGServiceOverridesEntity serviceOverridesEntity) {
    return String.join("_", serviceOverridesEntity.getEnvironmentRef()).replace(".", "_");
  }
}
