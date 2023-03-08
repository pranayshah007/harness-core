/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import io.harness.account.AccountClient;
import io.harness.delegate.beans.DelegateRing;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.persistence.HPersistence;
import io.harness.remote.client.CGRestUtils;
import io.harness.service.intfc.AccountDataProvider;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class AccountDataProviderImpl implements AccountDataProvider {
  private final AccountClient accountClient;

  private final HPersistence persistence;

  @Override
  public DelegateRing getDelegateRing(String accountId) {
    AccountDTO response = CGRestUtils.getResponse(accountClient.getAccountDTO(accountId));
    return persistence.createQuery(DelegateRing.class)
        .filter(DelegateRing.DelegateRingKeys.ringName, response.getRingName())
        .get();
  }
}
