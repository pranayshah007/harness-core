/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import io.harness.delegate.service.core.client.DelegateCoreManagerClient;
import io.harness.delegate.service.core.client.DelegateCoreManagerClientFactory;

import com.google.inject.AbstractModule;

public class ClientModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DelegateCoreManagerClient.class).toProvider(DelegateCoreManagerClientFactory.class);
  }
}
