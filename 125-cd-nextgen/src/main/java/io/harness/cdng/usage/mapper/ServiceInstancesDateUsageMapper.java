/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.mapper;

import static io.harness.NGDateUtils.YEAR_MONTH_DAY_DATE_PATTERN;
import static io.harness.NGDateUtils.getLocalDateOrThrow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.usage.dto.LicenseDateUsageParams;
import io.harness.cdng.usage.pojos.ServiceInstancesDateUsageFetchData;

import java.time.LocalDate;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ServiceInstancesDateUsageMapper {
  public static ServiceInstancesDateUsageFetchData buildServiceInstancesDateUsageFetchData(
      String accountIdentifier, LicenseDateUsageParams licenseDateUsageParams) {
    LocalDate fromDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, licenseDateUsageParams.getFromDate());
    LocalDate toDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, licenseDateUsageParams.getToDate());
    return ServiceInstancesDateUsageFetchData.builder()
        .accountIdentifier(accountIdentifier)
        .fromDate(fromDate)
        .toDate(toDate)
        .reportType(licenseDateUsageParams.getReportType())
        .build();
  }
}
