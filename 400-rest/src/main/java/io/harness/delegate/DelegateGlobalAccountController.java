/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate;

import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class DelegateGlobalAccountController {
  private final AtomicReference<Optional<Account>> globalDelegateAccountRef = new AtomicReference<>();
  @Inject private HPersistence persistence;

  public DelegateGlobalAccountController() {}

  public Optional<Account> getGlobalAccount() {
    if (globalDelegateAccountRef.get() == null || !globalDelegateAccountRef.get().isPresent()) {
      Account account = persistence.createQuery(Account.class, excludeAuthorityCount)
                            .filter(AccountKeys.globalDelegateAccount, true)
                            .get();
      globalDelegateAccountRef.compareAndSet(null, Optional.ofNullable(account));
    }

    return globalDelegateAccountRef.get();
  }
}
