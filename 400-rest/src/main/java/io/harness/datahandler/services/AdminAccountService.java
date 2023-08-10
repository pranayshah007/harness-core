/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.datahandler.services;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureFlag;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.datahandler.models.AccountSummary;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.lib.Limit;

import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.LicenseUpdateInfo;

import java.util.List;

@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
public interface AdminAccountService {
  LicenseInfo updateLicense(String accountId, LicenseUpdateInfo licenseUpdateInfo);
  String disableTriggers(String accountId, String orgIdentifier, String projectIdentifier);
  CeLicenseInfo updateCeLicense(String accountId, CeLicenseInfo celicenseInfo);

  List<AccountSummary> getPaginatedAccountSummaries(String offset, int pageSize);

  AccountSummary getAccountSummaryByAccountId(String accountId);

  ConfiguredLimit updateLimit(String accountId, ActionType actionType, Limit limit);

  LicenseInfo getLicense(String accountId);

  CeLicenseInfo getCeLicense(String accountId);

  List<ConfiguredLimit> getLimitsConfiguredForAccount(String accountId);

  ConfiguredLimit getLimitConfiguredByActionType(String accountId, ActionType actionType);

  Account createAccount(Account account, String adminUserEmail);

  boolean enableAccount(String accountId);

  boolean disableAccount(String accountId, String newClusterUrl);

  boolean enableOrDisableCloudCost(String accountId, boolean enabled);

  boolean enableOrDisableCeK8sEventCollection(String accountId, boolean ceK8sEventCollectionEnabled);

  boolean delete(String accountId);

  FeatureFlag updateFeatureFlagForAccount(String accountId, String featureName, boolean enabled);

  boolean updatePovFlag(String accountId, boolean isPov);

  boolean updateAccountName(String accountId, String accountName);

  boolean updateCompanyName(String accountId, String companyName);

  boolean enableOrDisableNextGen(String accountId, boolean enabled);

  boolean syncNextgenWithCG(String accountId);

  boolean cleanUpNextGen(String accountId);

  boolean disableIpAllowList(String accountId);

  boolean updateIsProductLed(String accountId, boolean enabled);

  boolean updateRingName(String accountId, String ringName);

  Account createGlobalDelegateAccount(Account account, String adminUserEmail);
}
