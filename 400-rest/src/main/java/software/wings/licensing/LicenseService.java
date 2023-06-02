/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.licensing;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;

import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.GTM)
@TargetModule(HarnessModule._945_ACCOUNT_MGMT)
public interface LicenseService {
  String LICENSE_INFO = "LICENSE_INFO";

  void checkForLicenseExpiry(Account account);

  boolean updateAccountLicense(@NotEmpty String accountId, LicenseInfo licenseInfo);

  boolean updateCeLicense(@NotEmpty String accountId, CeLicenseInfo ceLicenseInfo);

  boolean startCeLimitedTrial(@NotEmpty String accountId);

  Account updateAccountSalesContacts(@NotEmpty String accountId, List<String> salesContacts);

  void updateAccountLicenseForOnPrem(String encryptedLicenseInfoBase64String);

  boolean isAccountDeleted(String accountId);

  boolean isAccountExpired(String accountId);

  boolean isAccountExpired(Account account);

  void validateLicense(String accountId, String operation);

  void setLicense(Account account);

  Edition getDimensionPlan(String dimension);

  Long getNumberOfClientMAUs(Edition plan);

  LicenseType getModuleLicenseType(Edition plan);

  boolean hasPremierSupport(String dimension);

  boolean updateLicenseForProduct(
      String productCode, String accountId, Integer orderQuantity, long expirationTime, String dimension);
}
