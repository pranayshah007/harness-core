/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.module;

import io.harness.delegate.authenticator.DelegateServiceTokenAuthenticatorImpl;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.service.impl.DelegateAuthServiceImpl;
import io.harness.service.intfc.DelegateAuthService;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DelegateAuthModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DelegateAuthService.class).to(DelegateAuthServiceImpl.class);
    bind(DelegateTokenAuthenticator.class).to(DelegateServiceTokenAuthenticatorImpl.class).in(Singleton.class);
  }
}
