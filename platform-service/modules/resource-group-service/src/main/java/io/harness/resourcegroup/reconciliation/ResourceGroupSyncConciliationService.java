/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.reconciliation;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceGroupSyncConciliationService implements Managed {
  final Consumer consumer;
  final Map<String, Resource> resources;
  final ResourceGroupService resourceGroupService;
  final String serviceId;

  final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("resourcegroup-reconciliation-job").build());

  private Future<?> job;

  @Inject
  public ResourceGroupSyncConciliationService(@Named(EventsFrameworkConstants.ENTITY_CRUD) Consumer consumer,
      Map<String, Resource> resources, ResourceGroupService resourceGroupService,
      @Named("serviceId") String serviceId) {
    this.consumer = consumer;
    this.resources = resources;
    this.resourceGroupService = resourceGroupService;
    this.serviceId = serviceId;
  }

  @Override
  public void start() {
    job = executorService.scheduleAtFixedRate(
        new ResourceGroupSyncConciliationJob(consumer, resources, resourceGroupService, serviceId), 5, 5,
        TimeUnit.SECONDS);
  }

  @Override
  public void stop() throws Exception {
    if (job != null) {
      job.cancel(true);
    }
    executorService.shutdownNow();
    executorService.awaitTermination(5, TimeUnit.SECONDS);
  }
}
