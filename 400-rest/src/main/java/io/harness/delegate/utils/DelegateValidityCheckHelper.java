/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.delegate.message.ManagerMessageConstants.MIGRATE;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupStatus;
import io.harness.persistence.HPersistence;

import software.wings.licensing.LicenseService;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
@Singleton
public class DelegateValidityCheckHelper {
  @Inject private LicenseService licenseService;
  @Inject private HPersistence persistence;
  @Inject private AccountService accountService;

  public Optional<String> getBroadcastMessageFromDelegateValidityCheck(@NotNull final Delegate existingDelegate) {
    if (licenseService.isAccountDeleted(existingDelegate.getAccountId())) {
      log.warn("Sending self destruct command from register delegate because the account is deleted.");
      return Optional.of(SELF_DESTRUCT);
    }

    if (isNotBlank(existingDelegate.getDelegateGroupId())) {
      DelegateGroup delegateGroup = persistence.get(DelegateGroup.class, existingDelegate.getDelegateGroupId());
      if (delegateGroup == null || DelegateGroupStatus.DELETED == delegateGroup.getStatus()) {
        log.warn("Sending self destruct command from register delegate because the delegate group is deleted.");
        return Optional.of(SELF_DESTRUCT);
      }
    }

    if (accountService.isAccountMigrated(existingDelegate.getAccountId())) {
      return Optional.of(MIGRATE + accountService.get(existingDelegate.getAccountId()).getMigratedToClusterUrl());
    }

    return Optional.empty();
  }
}
