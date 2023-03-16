/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import static io.harness.delegate.beans.DelegateRing.DelegateRingKeys;

import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.AccountDataProvider;

import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class AccountDataProviderImpl implements AccountDataProvider {
  private final HPersistence persistence;

  private final AccountService accountService;

  @Override
  public DelegateRing getDelegateRing(String accountId) {
    return persistence.createQuery(DelegateRing.class)
        .filter(DelegateRingKeys.ringName, accountService.get(accountId).getRingName())
        .get();
  }
}
