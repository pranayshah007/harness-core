/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.serviceproviders;

import io.harness.delegate.DelegateConfigurationServiceProvider;
import io.harness.delegate.DelegatePropertiesServiceProvider;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.secrets.SecretsDelegateCacheHelperService;
import io.harness.secrets.SecretsDelegateCacheService;

import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.AbstractModule;

public class ServiceProvidersModule extends AbstractModule {
  @Override
  public void configure() {
    bind(DelegateConfigurationServiceProvider.class).to(DelegateAccountIdProvider.class);
    bind(DelegateFileManagerBase.class).to(DelegateFileManagerNoopImpl.class);
    bind(DelegateLogService.class).to(DelegateLogServiceNoopImpl.class);
    bind(DelegatePropertiesServiceProvider.class).to(DelegatePropertiesServiceProviderNoopImpl.class);
    bind(SecretsDelegateCacheService.class).to(SecretDelegateCacheServiceNoopImpl.class);
    bind(SecretsDelegateCacheHelperService.class).to(SecretsDelegateCacheHelperServiceNoopImpl.class);
  }
}
