/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.module;

import io.harness.dms.configuration.DelegateServiceConfiguration;
import io.harness.eventframework.EventsFrameworkModule;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.service.impl.AccountDataProviderImpl;
import io.harness.service.impl.DMSAssignDelegateServiceImpl;
import io.harness.service.impl.DMSTaskServiceImpl;
import io.harness.service.impl.DelegateRingServiceImpl;
import io.harness.service.intfc.AccountDataProvider;
import io.harness.service.intfc.DMSAssignDelegateService;
import io.harness.service.intfc.DMSTaskService;
import io.harness.service.intfc.DelegateRingService;

import software.wings.service.impl.DMSMongoDataStoreServiceImpl;
import software.wings.service.impl.DelegateSelectionLogsServiceImpl;
import software.wings.service.intfc.DMSDataStoreService;
import software.wings.service.intfc.DelegateSelectionLogsService;

import com.google.inject.AbstractModule;
import java.time.Clock;

/*
Creating a separate Module for bindings to be used in DMS.
We are having two implementations of a service, DMS specific implementation and manager side implementation.

This file will serve binding services with DMS side implementations.
Separate Module is needed because manager uses DelegateServiceModule already.
 */
public class DmsModule extends AbstractModule {
  private final DelegateServiceConfiguration config;

  public DmsModule(DelegateServiceConfiguration config) {
    this.config = config;
  }
  @Override
  protected void configure() {
    bind(DelegateRingService.class).to(DelegateRingServiceImpl.class);
    bind(AccountDataProvider.class).to(AccountDataProviderImpl.class);
    bind(DMSTaskService.class).to(DMSTaskServiceImpl.class);
    bind(DMSAssignDelegateService.class).to(DMSAssignDelegateServiceImpl.class);
    bind(DelegateSelectionLogsService.class).to(DelegateSelectionLogsServiceImpl.class);
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder()
                        .logStreamingServiceBaseUrl(config.getLogStreamingServiceConfig().getBaseUrl())
                        .build());
    bind(DMSDataStoreService.class).to(DMSMongoDataStoreServiceImpl.class);
    install(new EventsFrameworkModule(config.getEventsFrameworkConfiguration()));
  }
}
