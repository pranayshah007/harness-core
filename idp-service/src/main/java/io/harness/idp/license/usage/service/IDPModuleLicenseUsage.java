/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface IDPModuleLicenseUsage {
  /**
   * Checks if the URL path qualifies for IDP license usage capture
   * @param urlPath URL path to check for IDP license usage capture
   * @return boolean true / false on whether URL path qualifies for IDP license usage capture
   */
  boolean checkIfUrlPathCapturesLicenseUsage(String urlPath);

  /**
   * Captures idp module license usage with account & user details in redis
   * @param idpLicenseUsageUserCapture DTO containing account and user details for idp module license usage capture
   */
  void captureLicenseUsageInRedis(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture);

  /**
   * Saves IDP module license usage (account - user - lastAccessedAt) in database
   * @param idpLicenseUsageUserCapture DTO containing account and user details for idp module license usage capture
   */
  void saveLicenseUsageInDB(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture);

  /**
   * Aggregates previous day developers count per account
   */
  void licenseUsageDailyCountAggregationPerAccount();

  /**
   * Fetch IDP module license usage history (daily for given month / monthly for last one year) for given account
   * @param accountIdentifier Account identifier to fetch data for
   * @param idpLicenseDateUsageParams Time period to fetch data for (daily for given month / monthly for last one year)
   * @return IDP module license usage history (daily for given month / monthly for last one year) for given account
   */
  List<ActiveDevelopersTrendCountDTO> getHistoryTrend(
      String accountIdentifier, IDPLicenseDateUsageParams idpLicenseDateUsageParams);
}
