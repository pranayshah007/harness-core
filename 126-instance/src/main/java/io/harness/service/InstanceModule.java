/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InstanceSyncPerpetualTaskMappingService;
import io.harness.entities.InstanceSyncPerpetualTaskMappingServiceImpl;
import io.harness.instancesyncmonitoring.module.InstanceSyncMonitoringModule;
import io.harness.persistence.HPersistence;
import io.harness.repositories.instancestats.InstanceStatsRepository;
import io.harness.repositories.instancestats.InstanceStatsRepositoryImpl;
import io.harness.repositories.instancestatsiterator.InstanceStatsIteratorRepository;
import io.harness.repositories.instancestatsiterator.InstanceStatsIteratorRepositoryImpl;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.deploymentsummary.DeploymentSummaryServiceImpl;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.infrastructuremapping.InfrastructureMappingServiceImpl;
import io.harness.service.instance.InstanceService;
import io.harness.service.instance.InstanceServiceImpl;
import io.harness.service.instancedashboardservice.InstanceDashboardService;
import io.harness.service.instancedashboardservice.InstanceDashboardServiceImpl;
import io.harness.service.instancestats.InstanceStatsService;
import io.harness.service.instancestats.InstanceStatsServiceImpl;
import io.harness.service.instancesync.GitopsInstanceSyncService;
import io.harness.service.instancesync.GitopsInstanceSyncServiceImpl;
import io.harness.service.instancesync.InstanceSyncService;
import io.harness.service.instancesync.InstanceSyncServiceImpl;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryServiceImpl;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskServiceImpl;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoServiceImpl;
import io.harness.service.stats.statscollector.InstanceStatsCollectorImpl;
import io.harness.service.stats.statscollector.StatsCollector;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisherImpl;

import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.DefaultBroadcasterFactory;

@OwnedBy(DX)
public class InstanceModule extends AbstractModule {
  private static final AtomicReference<InstanceModule> instanceRef = new AtomicReference<>();

  public static InstanceModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new InstanceModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bind(InstanceDashboardService.class).to(InstanceDashboardServiceImpl.class);
    bind(InstanceService.class).to(InstanceServiceImpl.class);
    bind(InstanceSyncPerpetualTaskInfoService.class).to(InstanceSyncPerpetualTaskInfoServiceImpl.class);
    bind(InfrastructureMappingService.class).to(InfrastructureMappingServiceImpl.class);
    bind(InstanceSyncHandlerFactoryService.class).to(InstanceSyncHandlerFactoryServiceImpl.class);
    bind(InstanceSyncService.class).to(InstanceSyncServiceImpl.class);
    bind(DeploymentSummaryService.class).to(DeploymentSummaryServiceImpl.class);
    bind(InstanceSyncPerpetualTaskService.class).to(InstanceSyncPerpetualTaskServiceImpl.class);
    bind(StatsCollector.class).to(InstanceStatsCollectorImpl.class);
    bind(InstanceStatsService.class).to(InstanceStatsServiceImpl.class);
    bind(UsageMetricsEventPublisher.class).to(UsageMetricsEventPublisherImpl.class);
    bind(InstanceStatsRepository.class).to(InstanceStatsRepositoryImpl.class);
    bind(InstanceStatsIteratorRepository.class).to(InstanceStatsIteratorRepositoryImpl.class);
    bind(BroadcasterFactory.class).to(DefaultBroadcasterFactory.class);
    bind(GitopsInstanceSyncService.class).to(GitopsInstanceSyncServiceImpl.class);
    bind(InstanceSyncPerpetualTaskMappingService.class).to(InstanceSyncPerpetualTaskMappingServiceImpl.class);
    install(new InstanceSyncMonitoringModule());
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
