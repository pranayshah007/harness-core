/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.marketplace.gcp.procurement;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.marketplace.gcp.GcpMarketPlaceConstants.DEFAULT_LICENCE_UNITS;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.account.AccountStatus;
import software.wings.beans.marketplace.gcp.GCPBillingJobEntity;
import software.wings.licensing.LicenseService;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.marketplace.gcp.GCPBillingPollingService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@OwnedBy(PL)
/**
 * This interface should be extended for each new Product that we add to GCP Marketplace
 */
public class CDProductHandler implements GcpProductHandler {
  @Inject private AccountService accountService;
  @Inject private LicenseService licenseService;
  @Inject private GCPBillingPollingService gcpBillingPollingService;

  @Override
  public void handleNewSubscription(String accountId, String plan) {
    licenseService.updateAccountLicense(accountId,
        LicenseInfo.builder()
            .accountType(AccountType.PAID)
            .licenseUnits(DEFAULT_LICENCE_UNITS)
            .accountStatus(AccountStatus.ACTIVE)
            .expiryTime(LicenseUtils.getDefaultPaidExpiryTime())
            .build());

    gcpBillingPollingService.create(
        new GCPBillingJobEntity(accountId, Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()));
  }

  @Override
  public void handlePlanChange(String accountId, String newPlan) {
    LicenseInfo existingLicenseInfo = accountService.get(accountId).getLicenseInfo();
    licenseService.updateAccountLicense(accountId,
        LicenseInfo.builder()
            .accountType(existingLicenseInfo.getAccountType())
            .licenseUnits(existingLicenseInfo.getLicenseUnits())
            .accountStatus(AccountStatus.ACTIVE)
            .expiryTime(new Date(LicenseUtils.getDefaultPaidExpiryTime()).getTime())
            .build());
  }

  @Override
  public void handleSubscriptionCancellation(String accountId) {
    gcpBillingPollingService.delete(accountId);
  }
}
