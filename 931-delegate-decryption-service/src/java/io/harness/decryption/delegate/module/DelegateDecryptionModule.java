/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.decryption.delegate.module;

import io.harness.secrets.noop.NoopSecretsDelegateCacheHelperService;
import io.harness.secrets.SecretsDelegateCacheHelperService;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.secrets.SecretsDelegateCacheServiceImpl;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.threading.ThreadPool;

import software.wings.service.impl.security.DelegateDecryptionServiceImpl;
import software.wings.service.impl.security.EncryptionServiceImpl;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class DelegateDecryptionModule extends AbstractModule {
  @Provides
  @Singleton
  @Named("asyncExecutor")
  public ExecutorService asyncExecutor() {
    return ThreadPool.create(10, 400, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("async-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  @Override
  protected void configure() {
    bind(DelegateDecryptionService.class).to(DelegateDecryptionServiceImpl.class);
    bind(EncryptionService.class).to(EncryptionServiceImpl.class);
    bind(SecretsDelegateCacheService.class).to(SecretsDelegateCacheServiceImpl.class);
    bind(SecretsDelegateCacheHelperService.class).to(NoopSecretsDelegateCacheHelperService.class);
    // Needed for configurable secret cache expiery
    //    bind(DelegateConfigurationServiceProvider.class).to(DelegateConfigurationServiceProviderImpl.class);
    //    bind(DelegatePropertiesServiceProvider.class).to(DelegatePropertiesServiceProviderImpl.class);
  }
}
