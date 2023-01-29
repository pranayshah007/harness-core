/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.client;

import io.harness.cvng.notification.config.ErrorTrackingClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class ErrorTrackingClientModule extends AbstractModule {
  private final ErrorTrackingClientConfig errorTrackingClientConfig;

  public ErrorTrackingClientModule(ErrorTrackingClientConfig errorTrackingClientConfig) {
    this.errorTrackingClientConfig = errorTrackingClientConfig;
  }

  @Override
  protected void configure() {
    bind(ErrorTrackingClient.class)
        .toProvider(
            new ErrorTrackingClientFactory(errorTrackingClientConfig.getErrorTrackingServiceConfig().getBaseUrl(),
                errorTrackingClientConfig.getErrorTrackingServiceSecret(), new ServiceTokenGenerator()))
        .in(Scopes.SINGLETON);
  }
}
