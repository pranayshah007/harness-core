/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.security.ServiceTokenGenerator;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Guice Module for initializing Verification Manager client.
 * Created by raghu on 09/17/18.
 */
public class VerificationManagerClientModule extends AbstractModule {
  private final String managerBaseUrl;

  public VerificationManagerClientModule(String managerBaseUrl) {
    this.managerBaseUrl = managerBaseUrl + (managerBaseUrl.endsWith("/") ? "api/" : "/api/");
  }

  @Override
  protected void configure() {
    ServiceTokenGenerator tokenGenerator = new ServiceTokenGenerator();
    bind(ServiceTokenGenerator.class).toInstance(tokenGenerator);
    bind(VerificationManagerClient.class)
        .toProvider(new VerificationManagerClientFactory(managerBaseUrl, tokenGenerator))
        .in(Singleton.class);
  }
}
