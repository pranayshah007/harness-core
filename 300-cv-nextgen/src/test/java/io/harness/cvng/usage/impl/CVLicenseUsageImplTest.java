/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CV)
class CVLicenseUsageImplTest extends CvNextGenTestBase {
  @Mock private MonitoredServiceService monitoredServiceService;
  @Inject private LicenseUsageInterface licenseUsageInterface;

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  public void testGetLicenseUsage() {
    when(monitoredServiceService.countUniqueEnabledServices(any())).thenReturn(5);
    CVLicenseUsageDTO cvLicenseUsageDTO =
        (CVLicenseUsageDTO) licenseUsageInterface.getLicenseUsage("testaccid", ModuleType.CV, 1, null);
  }
}
