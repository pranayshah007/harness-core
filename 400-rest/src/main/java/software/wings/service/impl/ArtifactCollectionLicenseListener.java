/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import software.wings.beans.Account;
import software.wings.beans.account.AccountStatus;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.account.AccountLicenseObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ArtifactCollectionLicenseListener implements AccountLicenseObserver {
  @Inject ArtifactStreamService artifactStreamService;
  @Inject AccountService accountService;

  @Override
  public boolean onLicenseChange(Account account) {
    if (account.getLicenseInfo() != null && AccountStatus.ACTIVE.equals(account.getLicenseInfo().getAccountStatus())) {
      log.info("Enabling artifact collection for accountId {}", account.getUuid());
      return artifactStreamService.resetStoppedArtifactCollectionForAccount(account.getUuid());
    } else {
      log.warn("Disabling artifact collection for accountId {}", account.getUuid());
      return artifactStreamService.stopArtifactCollectionForAccount(account.getUuid());
    }
  }

  @Override
  public boolean onLicenseChange(String accountId) {
    Account account = accountService.get(accountId);
    return onLicenseChange(account);
  }
}
