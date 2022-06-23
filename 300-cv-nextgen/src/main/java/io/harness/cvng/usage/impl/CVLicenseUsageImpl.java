/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.UsageRequestParams;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;

public class CVLicenseUsageImpl implements LicenseUsageInterface<CVLicenseUsageDTO, UsageRequestParams> {
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public CVLicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, UsageRequestParams usageRequest) {
    Preconditions.checkArgument(timestamp > 0, format("Invalid timestamp %d while fetching LicenseUsages.", timestamp));
    Preconditions.checkArgument(ModuleType.CV == module, format("Invalid Module type %s provided", module.toString()));
    Preconditions.checkArgument(
        StringUtils.isNotBlank(accountIdentifier), "Account Identifier cannot be null or blank");

    long count = monitoredServiceService.countUniqueEnabledServices(accountIdentifier);

    return CVLicenseUsageDTO.builder()
        .activeServices(UsageDataDTO.builder().count(count).displayName("Total active SRM services").build())
        .build();
  }
}
