/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.FAILED_TO_ACQUIRE_PERSISTENT_LOCK;
import static io.harness.exception.WingsException.SRE;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.beans.ActivityVerificationSummary;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.ActivityBucket;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.KubernetesClusterActivity;
import io.harness.cvng.activity.entities.PagerDutyActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.core.beans.dependency.KubernetesDependencyMetadata;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.exception.PersistentLockException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ActivityServiceImplTest extends CvNextGenTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private ActivityService activityService;
  @Mock private VerificationJobInstanceService verificationJobInstanceService;
  @Mock private PersistentLocker mockedPersistentLocker;
  @Inject private MonitoredServiceService monitoredServiceService;
  private String projectIdentifier;
  private String orgIdentifier;
  private String accountId;
  private Instant instant;
  private String serviceIdentifier;
  private String envIdentifier;
  private String deploymentTag;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    instant = Instant.parse("2020-07-27T10:44:06.390Z");
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    accountId = builderFactory.getContext().getAccountId();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    deploymentTag = "build#1";

    FieldUtils.writeField(activityService, "verificationJobInstanceService", verificationJobInstanceService, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpsert_createEntityIsEqual() {
    useMockedPersistentLocker();
    Activity activity = builderFactory.getDeploymentActivityBuilder().build();
    String activityUuid = activityService.upsert(activity);
    Activity activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpsert_createMultipleDeploymentActivity() {
    useMockedPersistentLocker();
    DeploymentActivity activity = builderFactory.getDeploymentActivityBuilder().build();
    String activityUuid = activityService.upsert(activity);
    Activity activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
    activity = builderFactory.getDeploymentActivityBuilder().build();
    activity.setEventTime(activity.getEventTime().plus(10, ChronoUnit.SECONDS));
    activity.setPlanExecutionId(generateUuid());
    activityUuid = activityService.upsert(activity);
    activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
    List<ActivityBucket> activityBuckets = hPersistence.createQuery(ActivityBucket.class).find().toList();
    assertThat(activityBuckets.size()).isEqualTo(1);
    assertThat(activityBuckets.get(0).getCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpsert_createMultiplePagerDutyActivity() {
    useMockedPersistentLocker();
    PagerDutyActivity activity = builderFactory.getPagerDutyActivityBuilder().build();
    String activityUuid = activityService.upsert(activity);
    Activity activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
    activity = builderFactory.getPagerDutyActivityBuilder().build();
    activity.setEventTime(activity.getEventTime().plus(10, ChronoUnit.SECONDS));
    activity.setEventId(generateUuid());
    activityUuid = activityService.upsert(activity);
    activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
    List<ActivityBucket> activityBuckets = hPersistence.createQuery(ActivityBucket.class).find().toList();
    assertThat(activityBuckets.size()).isEqualTo(1);
    assertThat(activityBuckets.get(0).getCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpsert_createMultipleKubernetesActivity() {
    useMockedPersistentLocker();
    KubernetesClusterActivity activity = builderFactory.getKubernetesClusterActivityBuilder().build();
    setMonitoredService(activity);
    String activityUuid = activityService.upsert(activity);
    Activity activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
    activity = builderFactory.getKubernetesClusterActivityBuilder().build();
    activity.setEventTime(activity.getEventTime().plus(10, ChronoUnit.SECONDS));
    activityUuid = activityService.upsert(activity);
    activityFromDb = activityService.get(activityUuid);
    assertThat(activityFromDb).isEqualTo(activity);
    List<ActivityBucket> activityBuckets = hPersistence.createQuery(ActivityBucket.class).find().toList();
    assertThat(activityBuckets.size()).isEqualTo(1);
    assertThat(activityBuckets.get(0).getCount()).isEqualTo(2);
  }

  private void setMonitoredService(KubernetesClusterActivity clusterActivity) {
    MonitoredServiceDTO infraService = builderFactory.monitoredServiceDTOBuilder()
                                           .type(MonitoredServiceType.INFRASTRUCTURE)
                                           .serviceRef(builderFactory.getContext().getServiceIdentifier() + "infra")
                                           .environmentRef(builderFactory.getContext().getEnvIdentifier())
                                           .build();
    infraService.getSources().setHealthSources(null);
    infraService.getSources().setChangeSources(
        Sets.newHashSet(builderFactory.getKubernetesChangeSourceDTOBuilder().build()));

    monitoredServiceService.create(clusterActivity.getAccountId(), infraService);

    MonitoredServiceDTO appService =
        builderFactory.monitoredServiceDTOBuilder()
            .identifier(generateUuid())
            .serviceRef(serviceIdentifier)
            .environmentRef(envIdentifier)
            .dependencies(Sets.newHashSet(
                MonitoredServiceDTO.ServiceDependencyDTO.builder()
                    .monitoredServiceIdentifier(
                        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())
                    .dependencyMetadata(KubernetesDependencyMetadata.builder()
                                            .namespace(clusterActivity.getNamespace())
                                            .workload(clusterActivity.getWorkload())
                                            .build())
                    .build()))
            .build();
    appService.getSources().setHealthSources(null);
    appService.getSources().setChangeSources(null);
    monitoredServiceService.create(clusterActivity.getAccountId(), appService);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpsert_updateEntity() {
    useMockedPersistentLocker();
    Activity existingActivity =
        builderFactory.getDeploymentActivityBuilder().planExecutionId("planExecutionId").build();
    Activity updatingActivity =
        builderFactory.getDeploymentActivityBuilder().planExecutionId("planExecutionId").build();
    List<String> verificationJobInstanceIds = new ArrayList<>();
    verificationJobInstanceIds.addAll(existingActivity.getVerificationJobInstanceIds());
    verificationJobInstanceIds.addAll(updatingActivity.getVerificationJobInstanceIds());
    ActivityVerificationSummary summary = createActivitySummary(Instant.now());
    summary.setTotal(2);
    summary.setPassed(2);
    summary.setProgress(0);

    List<VerificationJobInstance> jobInstances = Arrays.asList(builderFactory.verificationJobInstanceBuilder()
                                                                   .deploymentStartTime(Instant.now())
                                                                   .startTime(Instant.now())
                                                                   .uuid(verificationJobInstanceIds.get(0))
                                                                   .build(),
        builderFactory.verificationJobInstanceBuilder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now())
            .uuid(verificationJobInstanceIds.get(1))
            .build());
    when(verificationJobInstanceService.get(existingActivity.getVerificationJobInstanceIds())).thenReturn(jobInstances);
    when(verificationJobInstanceService.getActivityVerificationSummary(eq(jobInstances))).thenReturn(summary);

    activityService.upsert(existingActivity);
    String uuid = activityService.upsert(updatingActivity);

    Activity activityFromDb = activityService.get(uuid);
    assertThat(activityFromDb.getVerificationJobInstanceIds()).isEqualTo(verificationJobInstanceIds);
    assertThat(activityFromDb.getVerificationSummary()).isEqualTo(summary);
    assertThat(activityFromDb.getActivityName()).isEqualTo(updatingActivity.getActivityName());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpsert_whenEntityLocked() {
    useMockedPersistentLocker();
    when(mockedPersistentLocker.waitToAcquireLock(any(), any(), any(), any()))
        .thenThrow(new PersistentLockException("Lock not acquired", FAILED_TO_ACQUIRE_PERSISTENT_LOCK, SRE));
    Activity activity = builderFactory.getDeploymentActivityBuilder().build();
    assertThatThrownBy(() -> activityService.upsert(activity)).isInstanceOf(PersistentLockException.class);
    assertThat(activity.getUuid()).isNull();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpsert_lockClose() {
    useMockedPersistentLocker();
    AcquiredLock acquiredLock = mock(AcquiredLock.class);
    when(mockedPersistentLocker.waitToAcquireLock(any(), any(), any(), any())).thenReturn(acquiredLock);
    activityService.upsert(builderFactory.getDeploymentActivityBuilder().build());
    verify(acquiredLock).close();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testAbort_inNotStarted() {
    VerificationJob verificationJob = createVerificationJob();
    instant = Instant.now();
    String activityId = activityService.createActivity(builderFactory.getDeploymentActivityBuilder()
                                                           .verificationJobs(Collections.singletonList(verificationJob))
                                                           .build());
    Activity activity = activityService.get(activityId);
    List<String> verificationJobs = Lists.newArrayList("JOB_INSTANCE_ID");
    activity.setVerificationJobInstanceIds(verificationJobs);
    hPersistence.save(activity);

    activityService.abort(activityId);

    Activity updatedActivity = activityService.get(activityId);
    verify(verificationJobInstanceService).abort(Lists.newArrayList(updatedActivity.getVerificationJobInstanceIds()));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testAbort_inError() {
    VerificationJob verificationJob = createVerificationJob();
    instant = Instant.now();
    String activityId = activityService.createActivity(builderFactory.getDeploymentActivityBuilder()
                                                           .verificationJobs(Collections.singletonList(verificationJob))
                                                           .build());
    Activity activity = activityService.get(activityId);
    activity.setAnalysisStatus(ActivityVerificationStatus.ERROR);
    hPersistence.save(activity);

    activityService.abort(activityId);

    Activity updatedActivity = activityService.get(activityId);
    // assert that errored activity is not aborted
    assertThat(updatedActivity.getAnalysisStatus()).isEqualTo(ActivityVerificationStatus.ERROR);
    verify(verificationJobInstanceService, never()).abort(any());
  }

  private VerificationJob createVerificationJob() {
    CanaryVerificationJob testVerificationJob = new CanaryVerificationJob();
    testVerificationJob.setUuid(generateUuid());
    testVerificationJob.setAccountId(accountId);
    testVerificationJob.setIdentifier("identifier");
    testVerificationJob.setJobName(generateUuid());
    testVerificationJob.setMonitoringSources(Collections.singletonList("monitoringIdentifier"));
    testVerificationJob.setSensitivity(Sensitivity.MEDIUM);
    testVerificationJob.setServiceIdentifier(serviceIdentifier, false);
    testVerificationJob.setEnvIdentifier(envIdentifier, false);
    testVerificationJob.setDuration(Duration.ofMinutes(5));
    testVerificationJob.setProjectIdentifier(projectIdentifier);
    testVerificationJob.setOrgIdentifier(orgIdentifier);
    return testVerificationJob;
  }

  private ActivityVerificationSummary createActivitySummary(Instant startTime) {
    return ActivityVerificationSummary.builder()
        .total(1)
        .startTime(startTime.toEpochMilli())
        .risk(Risk.OBSERVE)
        .progress(1)
        .notStarted(0)
        .durationMs(Duration.ofMinutes(15).toMillis())
        .remainingTimeMs(1200000)
        .progressPercentage(25)
        .build();
  }

  @SneakyThrows
  private void useMockedPersistentLocker() {
    FieldUtils.writeField(activityService, "persistentLocker", mockedPersistentLocker, true);
  }
}
