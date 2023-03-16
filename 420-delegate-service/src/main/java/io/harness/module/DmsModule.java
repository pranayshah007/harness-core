/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.module;

import io.harness.service.impl.AccountDataProviderImpl;
import io.harness.service.impl.DelegateRingServiceImpl;
import io.harness.service.intfc.AccountDataProvider;
import io.harness.service.intfc.DelegateRingService;

import com.google.inject.AbstractModule;

/*
Creating a separate Module for bindings to be used in DMS.
We are having two implementations of a service, DMS specific implementation and manager side implementation.

This file will serve binding services with DMS side implementations.
Separate Module is needed because manager uses DelegateServiceModule already.
 */
public class DmsModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DelegateRingService.class).to(DelegateRingServiceImpl.class);
    bind(AccountDataProvider.class).to(AccountDataProviderImpl.class);
  }
}
