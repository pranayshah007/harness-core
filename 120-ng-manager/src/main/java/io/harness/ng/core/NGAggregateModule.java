/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.manage.ManagedExecutorService;
import io.harness.ng.core.api.AggregateAccountResourceService;
import io.harness.ng.core.api.AggregateOrganizationService;
import io.harness.ng.core.api.AggregateProjectService;
import io.harness.ng.core.api.AggregateUserGroupService;
import io.harness.ng.core.api.impl.AggregateAccountResourceServiceImpl;
import io.harness.ng.core.api.impl.AggregateOrganizationServiceImpl;
import io.harness.ng.core.api.impl.AggregateProjectServiceImpl;
import io.harness.ng.core.api.impl.AggregateUserGroupServiceImpl;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityManagementServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OwnedBy(PL)
public class NGAggregateModule extends AbstractModule {
  @Override
  protected void configure() {
    int poolSize = Runtime.getRuntime().availableProcessors() * 3;
    bind(ExecutorService.class)
        .annotatedWith(Names.named("aggregate-projects"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(poolSize)));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("aggregate-orgs"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(poolSize)));
    bind(AggregateProjectService.class).to(AggregateProjectServiceImpl.class);
    bind(AggregateOrganizationService.class).to(AggregateOrganizationServiceImpl.class);
    bind(AggregateAccountResourceService.class).to(AggregateAccountResourceServiceImpl.class);
    bind(AggregateUserGroupService.class).to(AggregateUserGroupServiceImpl.class);
    bind(ServiceEntityManagementService.class).to(ServiceEntityManagementServiceImpl.class);
  }
}
