/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDLicenseType;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@OwnedBy(HarnessTeam.CDP)
public interface CDLicenseUsageReportService {
  Optional<CDLicenseType> getCDLicenseTypePerAccount(String accountId);

  Optional<LicenseDailyUsage> getLatestLicenseDailyUsageRecord(String accountId, CDLicenseType licenseType);

  List<LicenseDailyUsage> generateLicenseDailyUsageReport(
      String accountId, CDLicenseType licenseType, LocalDate fromDate, LocalDate toDate);

  void insertBatchLicenseDailyUsageRecords(
      String accountId, CDLicenseType licenseType, List<LicenseDailyUsage> newLicenseDailyReport);

  Map<String, Integer> getLicenseUsagePerMonthsReport(
      String accountIdentifier, CDLicenseType licenseType, LocalDate fromMonth, LocalDate toMonth);

  Map<String, Integer> getLicenseUsagePerDaysReport(
      String accountIdentifier, CDLicenseType licenseType, LocalDate fromDay, LocalDate toDay);
}
