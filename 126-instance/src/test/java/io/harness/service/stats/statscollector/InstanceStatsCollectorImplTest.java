/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.statscollector;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.ng.core.entities.Project;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancestats.InstanceStatsService;
import io.harness.service.stats.model.InstanceCountByServiceAndEnv;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;

import java.time.Instant;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.query.MorphiaIterator;
import org.mongodb.morphia.query.Query;

public class InstanceStatsCollectorImplTest extends InstancesTestBase {
  private static final String ACCOUNT_ID = "acc";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "proj";
  private static final String SERVICE_ID = "svc";
  private static final String ENV_ID = "env";
  private static final int SYNC_INTERVAL_MINUTES = 30;
  private static final int MAX_EVENTS_PER_PROJECT = 48;

  @Mock private InstanceStatsService instanceStatsService;
  @Mock private InstanceService instanceService;
  @Mock private HPersistence persistence;
  @Mock private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Mock(answer = RETURNS_SELF) private Query<Project> projectsQuery;
  @Mock private MorphiaIterator<Project, Project> projectIterator;
  @InjectMocks InstanceStatsCollectorImpl instanceStatsCollector;

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsTest() throws Exception {
    Instant lastSnapshot = Instant.now().minusSeconds((SYNC_INTERVAL_MINUTES * 2 + 5) * 60L);
    Project mockProject = mockProject();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv = mockInstanceCountByServiceAndEnv();
    when(instanceStatsService.getLastSnapshotTime(mockProject)).thenReturn(lastSnapshot);
    when(instanceService.getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong()))
        .thenReturn(Collections.singletonList(instanceCountByServiceAndEnv));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceService, times(2)).getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsLimitTest() throws Exception {
    Instant lastSnapshot = Instant.now().minusSeconds((SYNC_INTERVAL_MINUTES * (MAX_EVENTS_PER_PROJECT + 1) + 5) * 60L);
    Project mockProject = mockProject();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv = mockInstanceCountByServiceAndEnv();
    when(instanceStatsService.getLastSnapshotTime(mockProject)).thenReturn(lastSnapshot);
    when(instanceService.getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong()))
        .thenReturn(Collections.singletonList(instanceCountByServiceAndEnv));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceService, times(MAX_EVENTS_PER_PROJECT))
        .getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsTestForANewProject() {
    Project mockProject = mockProject();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv = mockInstanceCountByServiceAndEnv();
    when(instanceService.getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong()))
        .thenReturn(Collections.singletonList(instanceCountByServiceAndEnv));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceService, times(1)).getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void createStatsTestException() {
    Project mockProject = mockProject();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv = mockInstanceCountByServiceAndEnv();
    when(instanceService.getActiveInstancesByServiceAndEnv(eq(mockProject), anyLong()))
        .thenReturn(Collections.singletonList(instanceCountByServiceAndEnv));
    doThrow(new EventsFrameworkDownException("Cannot connect to redis stream"))
        .when(usageMetricsEventPublisher)
        .publishInstanceStatsTimeSeries(
            eq(mockProject), anyLong(), eq(Collections.singletonList(instanceCountByServiceAndEnv)));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isFalse();
  }

  private Project mockProject() {
    Project mockProject =
        Project.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).identifier(PROJECT_ID).build();
    when(persistence.createQuery(Project.class)).thenReturn(projectsQuery);
    when(projectsQuery.fetch(persistence.analyticNodePreferenceOptions())).thenReturn(projectIterator);
    when(projectIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(projectIterator.next()).thenReturn(mockProject);
    return mockProject;
  }

  private InstanceCountByServiceAndEnv mockInstanceCountByServiceAndEnv() {
    return InstanceCountByServiceAndEnv.builder()
        .serviceIdentifier(SERVICE_ID)
        .envIdentifier(ENV_ID)
        .count(5)
        .firstDocument(Instance.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .orgIdentifier(ORG_ID)
                           .projectIdentifier(PROJECT_ID)
                           .serviceIdentifier(SERVICE_ID)
                           .envIdentifier(ENV_ID)
                           .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
                           .build())
        .build();
  }
}
