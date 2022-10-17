/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.task.citasks.cik8handler.helper.DelegateServiceTokenHelper;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DelegateTokensModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    long start = System.currentTimeMillis();
    bind(DelegateServiceTokenHelper.class)
        .toInstance(DelegateServiceTokenHelper.builder()
                        .serviceTokenGenerator(new ServiceTokenGenerator())
                        .accountSecret(configuration.getDelegateToken())
                        .build());
    log.info("time taken to intialize DelegateTokensModule in ms {} ", System.currentTimeMillis() - start);
  }
}
