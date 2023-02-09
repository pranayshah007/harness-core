/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.provider;

import io.harness.secrets.serviceproviders.DelegateConfigurationServiceProvider;
import io.harness.delegate.configuration.DelegateConfiguration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DelegateConfigurationServiceProviderImpl implements DelegateConfigurationServiceProvider {
  @Inject DelegateConfiguration delegateConfiguration;

  @Override
  public String getAccount() {
    return delegateConfiguration.getAccountId();
  }
}
