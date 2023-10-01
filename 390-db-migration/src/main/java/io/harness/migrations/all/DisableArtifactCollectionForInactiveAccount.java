/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.account.AccountStatus;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.SPG)
@Singleton
@Slf4j
public class DisableArtifactCollectionForInactiveAccount implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject ArtifactStreamService artifactStreamService;

  @Override
  public void migrate() {
    log.info("Starting migration");
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        log.info("Starting Migration for account {}", account.getUuid());
        changeArtifactCollectionStatus(account);
      }
    } catch (Exception ex) {
      log.info(" Exception doing migration", ex);
    }
  }

  private void changeArtifactCollectionStatus(Account account) {
    if (account.getLicenseInfo() != null && !AccountStatus.ACTIVE.equals(account.getLicenseInfo().getAccountStatus())) {
      log.info("Disabling artifact collection for account {}", account.getUuid());
      artifactStreamService.stopArtifactCollectionForAccount(account.getUuid());
    }
  }
}
