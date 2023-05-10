/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.azurevmpricing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;

@OwnedBy(CE)
public class AzureVmPricingClientModule extends AbstractModule {
  private final ServiceHttpClientConfig httpClientConfig;

  @Inject
  public AzureVmPricingClientModule(ServiceHttpClientConfig httpClientConfig) {
    this.httpClientConfig = httpClientConfig;
  }

  @Provides
  private AzureVmPricingHttpClientFactory providesHttpClientFactory() {
    return new AzureVmPricingHttpClientFactory(this.httpClientConfig);
  }

  @Override
  protected void configure() {
    this.bind(AzureVmPricingClient.class).toProvider(AzureVmPricingHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
