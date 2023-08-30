/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.datahandler.models;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgLicenseModel;
import io.harness.ng.core.account.DefaultExperience;

import software.wings.beans.LicenseInfo;

import lombok.Data;

@OwnedBy(GTM)
@Data
@TargetModule(_955_ACCOUNT_MGMT)
public class AccountDetails {
  private String accountId;
  private String accountName;
  private String companyName;
  private String cluster;
  private boolean createdFromNG;
  private DefaultExperience defaultExperience;
  private boolean isCrossGenerationAccessEnabled;
  private LicenseInfo licenseInfo;
  private CeLicenseInfo ceLicenseInfo;
  private int activeServiceCount;
  private CgActiveServicesUsageInfo activeServicesUsageInfo;
  private CgLicenseModel licenseModel;
  private Integer sessionTimeOutInMinutes;
  private boolean publicAccessEnabled;
}
