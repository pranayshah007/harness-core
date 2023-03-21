/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.entities.SSCAServiceConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;

@OwnedBy(HarnessTeam.SSCA)
public class SSCAServiceClientModule extends AbstractModule {
  SSCAServiceConfig sscaServiceConfig;

  @Inject
  public SSCAServiceClientModule(SSCAServiceConfig sscaServiceConfig) {
    this.sscaServiceConfig = sscaServiceConfig;
  }

  @Override
  protected void configure() {
    this.bind(SSCAServiceConfig.class).toInstance(this.sscaServiceConfig);
    this.bind(SSCAServiceClient.class).toProvider(SSCAServiceClientFactory.class).in(Scopes.SINGLETON);
  }
}
