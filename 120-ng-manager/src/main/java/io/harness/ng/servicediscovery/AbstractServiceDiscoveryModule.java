/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicediscovery;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.servicediscovery.client.remote.ServiceDiscoveryClientModule;

import com.google.inject.AbstractModule;

public abstract class AbstractServiceDiscoveryModule extends AbstractModule {
  @Override
  protected void configure() {
    install(ServiceDiscoveryModule.getInstance());
    install(new ServiceDiscoveryClientModule(serviceDiscoveryClientConfig(), serviceSecret(), clientId()));
  }

  public abstract ServiceHttpClientConfig serviceDiscoveryClientConfig();

  public abstract String serviceSecret();

  public abstract String clientId();
}
