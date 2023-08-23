/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.statscollector;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.persistence.HPersistence;
import io.harness.repositories.instancestatsiterator.InstanceStatsIteratorRepository;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancestats.InstanceStatsService;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;

import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceStatsCollectorImplTest extends InstancesTestBase {
  private static final String ACCOUNT_ID = "acc";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "proj";
  private static final String SERVICE_ID = "svc";
  private static final int SYNC_INTERVAL_MINUTES = 30;
  private static final int MAX_CALLS_PER_SERVICE = 48;

  @Mock private InstanceStatsIteratorRepository instanceStatsIteratorRepository;
  @Mock private InstanceStatsService instanceStatsService;
  @Mock private InstanceService instanceService;
  @Mock private HPersistence persistence;
  @Mock private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Mock(answer = RETURNS_SELF) private Query<ServiceEntity> servicesQuery;
  @Mock private MorphiaIterator<ServiceEntity, ServiceEntity> serviceEntityIterator;
  @InjectMocks InstanceStatsCollectorImpl instanceStatsCollector;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void createStatsTest() throws Exception {
    Instant lastSnapshot = Instant.now().minusSeconds((SYNC_INTERVAL_MINUTES + 5) * 60L);
    InstanceDTO instanceDTO = InstanceDTO.builder().build();
    mockServices();
    when(instanceStatsService.getLastSnapshotTime(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID)).thenReturn(lastSnapshot);
    when(instanceService.getActiveInstancesByAccountOrgProjectAndService(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong()))
        .thenReturn(Collections.singletonList(instanceDTO));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceStatsIteratorRepository, times(1))
        .updateTimestampForIterator(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong());
    verify(instanceService, times(1))
        .getActiveInstancesByAccountOrgProjectAndService(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsTestForANewService() throws InterruptedException {
    InstanceDTO instanceDTO = InstanceDTO.builder().build();
    mockServices();
    when(instanceService.getActiveInstancesByAccountOrgProjectAndService(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong()))
        .thenReturn(Collections.singletonList(instanceDTO));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceStatsIteratorRepository, times(1))
        .updateTimestampForIterator(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong());
    verify(instanceService, times(1))
        .getActiveInstancesByAccountOrgProjectAndService(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsTestException() {
    List<InstanceDTO> instances = Collections.singletonList(InstanceDTO.builder().build());
    mockServices();
    when(instanceService.getActiveInstancesByAccountOrgProjectAndService(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong()))
        .thenReturn(instances);
    doThrow(new EventsFrameworkDownException("Cannot connect to redis stream"))
        .when(usageMetricsEventPublisher)
        .publishInstanceStatsTimeSeries(eq(ACCOUNT_ID), anyLong(), eq(instances));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsLimitTest() throws Exception {
    Instant lastSnapshot = Instant.now().minusSeconds((SYNC_INTERVAL_MINUTES * (MAX_CALLS_PER_SERVICE + 1) + 5) * 60L);
    InstanceDTO instanceDTO = InstanceDTO.builder().build();
    mockServices();
    when(instanceStatsService.getLastSnapshotTime(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID)).thenReturn(lastSnapshot);
    when(instanceService.getActiveInstancesByAccountOrgProjectAndService(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong()))
        .thenReturn(Collections.singletonList(instanceDTO));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceStatsIteratorRepository, times(1 + MAX_CALLS_PER_SERVICE))
        .updateTimestampForIterator(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong());
    verify(instanceService, times(MAX_CALLS_PER_SERVICE))
        .getActiveInstancesByAccountOrgProjectAndService(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void createStatsLimitTestException() throws Exception {
    List<InstanceDTO> instances = Collections.singletonList(InstanceDTO.builder().build());
    mockServices();
    when(instanceService.getActiveInstancesByAccountOrgProjectAndService(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), anyLong()))
        .thenReturn(instances);
    doThrow(new Exception("Cannot connect to redis stream"))
        .when(instanceStatsService)
        .getLastSnapshotTime(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isFalse();
  }

  private void mockServices() {
    when(persistence.createQuery(ServiceEntity.class)).thenReturn(servicesQuery);
    when(servicesQuery.fetch()).thenReturn(serviceEntityIterator);
    when(serviceEntityIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(serviceEntityIterator.next())
        .thenReturn(ServiceEntity.builder()
                        .accountId(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .identifier(SERVICE_ID)
                        .build());
  }
}
