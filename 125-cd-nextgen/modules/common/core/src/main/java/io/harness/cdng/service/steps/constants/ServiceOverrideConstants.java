/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps.constants;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.CLUSTER_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.CLUSTER_SERVICE_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_SERVICE_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_SERVICE_OVERRIDE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.Map;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDP)
@UtilityClass
public class ServiceOverrideConstants {
  public static Map<String, String> overrideMapper = Map.of(SERVICE.toString(), "service",
      ENV_GLOBAL_OVERRIDE.toString(), "Environment override", ENV_SERVICE_OVERRIDE.toString(),
      "Environment Service override", INFRA_GLOBAL_OVERRIDE.toString(), "Infrastructure override",
      INFRA_SERVICE_OVERRIDE.toString(), "Infrastructure Service override", CLUSTER_GLOBAL_OVERRIDE.toString(),
      "Cluster override", CLUSTER_SERVICE_OVERRIDE.toString(), "Cluster Service override");
}
