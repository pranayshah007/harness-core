/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import io.harness.chaos.client.remote.ChaosClientModule;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.google.inject.AbstractModule;

public abstract class AbstractChaosModule extends AbstractModule {
  @Override
  protected void configure() {
    install(ChaosModule.getInstance());
    install(new ChaosClientModule(chaosClientConfig(), serviceSecret(), clientId()));
  }

  public abstract ServiceHttpClientConfig chaosClientConfig();

  public abstract String serviceSecret();

  public abstract String clientId();
}
