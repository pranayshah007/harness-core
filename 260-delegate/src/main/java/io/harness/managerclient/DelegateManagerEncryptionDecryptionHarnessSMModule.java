/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.managerclient;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.security.TokenGenerator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DelegateManagerEncryptionDecryptionHarnessSMModule extends AbstractModule {
  private final DelegateConfiguration delegateConfiguration;

  @Provides
  @Singleton
  DelegateManagerEncryptionDecryptionHarnessSMClientFactory DelegateManagerEncryptionDecryptionHarnessSMClientFactory(
      final TokenGenerator tokenGenerator) {
    return new DelegateManagerEncryptionDecryptionHarnessSMClientFactory(delegateConfiguration, tokenGenerator);
  }

  @Override
  protected void configure() {
    bind(DelegateManagerEncryptionDecryptionHarnessSMClient.class)
        .toProvider(DelegateManagerEncryptionDecryptionHarnessSMClientFactory.class);
  }
}
