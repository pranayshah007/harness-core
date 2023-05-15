/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;



import io.harness.cdng.CDNGTestBase;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;

import com.google.inject.Inject;

class ServiceOverrideV2MigrationServiceImplTest extends CDNGTestBase {
  @Inject private ServiceOverridesRepositoryV2 serviceOverridesRepositoryV2;
  @Inject ServiceOverrideRepository serviceOverrideRepository;
  @Inject ServiceOverrideV2MigrationService v2MigrationService;


}
