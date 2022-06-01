/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.activity.beans.ActivityVerificationSummary;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.entities.Activity.ActivityUpdatableEntity;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityKeys;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;
import io.harness.cvng.analysis.beans.LogAnalysisClusterChartDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterDTO;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.activity.ActivityStatusDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class ActivityServiceImpl implements ActivityService {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private Map<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMap;
  @Inject private Map<ActivityType, ActivityUpdatableEntity> activityUpdatableEntityMap;
  @Inject private Map<ActivityType, ActivityUpdateHandler> activityUpdateHandlerMap;
  // TODO: remove the dependency once UI moves to new APIs
  @Inject private CVNGStepTaskService cvngStepTaskService;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public Activity get(String activityId) {
    Preconditions.checkNotNull(activityId, "ActivityID should not be null");
    return hPersistence.get(Activity.class, activityId);
  }

  @Override
  public Activity getByVerificationJobInstanceId(String verificationJobInstanceId) {
    return hPersistence.createQuery(Activity.class, excludeAuthority)
        .field(ActivityKeys.verificationJobInstanceIds)
        .contains(verificationJobInstanceId)
        .get();
  }

  @Override
  public String register(Activity activity) {
    activity.validate();
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    activity.getVerificationJobs().forEach(verificationJob -> {
      VerificationJobInstanceBuilder verificationJobInstanceBuilder = fillOutCommonJobInstanceProperties(
          activity, verificationJob.resolveAdditionsFields(verificationJobInstanceService));
      validateJob(verificationJob);
      activity.fillInVerificationJobInstanceDetails(verificationJobInstanceBuilder);

      verificationJobInstances.add(verificationJobInstanceBuilder.build());
    });
    activity.setVerificationJobInstanceIds(verificationJobInstanceService.create(verificationJobInstances));
    hPersistence.save(activity);
    log.info("Registered  an activity of type {} for account {}, project {}, org {}", activity.getType(),
        activity.getAccountId(), activity.getProjectIdentifier(), activity.getOrgIdentifier());
    return activity.getUuid();
  }

  @Override
  public void updateActivityStatus(Activity activity) {
    if (CollectionUtils.isEmpty(activity.getVerificationJobInstanceIds())) {
      return;
    }
    ActivityVerificationSummary summary = verificationJobInstanceService.getActivityVerificationSummary(
        verificationJobInstanceService.get(activity.getVerificationJobInstanceIds()));
    if (!summary.getAggregatedStatus().equals(ActivityVerificationStatus.IN_PROGRESS)
        && !summary.getAggregatedStatus().equals(ActivityVerificationStatus.NOT_STARTED)) {
      Query<Activity> activityQuery =
          hPersistence.createQuery(Activity.class).filter(ActivityKeys.uuid, activity.getUuid());
      UpdateOperations<Activity> activityUpdateOperations =
          hPersistence.createUpdateOperations(Activity.class)
              .set(ActivityKeys.analysisStatus, summary.getAggregatedStatus())
              .set(ActivityKeys.verificationSummary, summary);
      hPersistence.update(activityQuery, activityUpdateOperations);
      log.info("Updated the status of activity {} to {}", activity.getUuid(), summary.getAggregatedStatus());
    }
  }

  public String createActivity(Activity activity) {
    return hPersistence.save(activity);
  }

  private DeploymentVerificationJobInstanceSummary getDeploymentVerificationJobInstanceSummary(Activity activity) {
    List<String> verificationJobInstanceIds = activity.getVerificationJobInstanceIds();
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        verificationJobInstanceService.getDeploymentVerificationJobInstanceSummary(verificationJobInstanceIds);
    deploymentVerificationJobInstanceSummary.setActivityId(activity.getUuid());
    deploymentVerificationJobInstanceSummary.setActivityStartTime(activity.getActivityStartTime().toEpochMilli());
    return deploymentVerificationJobInstanceSummary;
  }
  @Override
  public ActivityStatusDTO getActivityStatus(String accountId, String activityId) {
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        getDeploymentVerificationJobInstanceSummary(get(activityId));
    return ActivityStatusDTO.builder()
        .durationMs(deploymentVerificationJobInstanceSummary.getDurationMs())
        .remainingTimeMs(deploymentVerificationJobInstanceSummary.getRemainingTimeMs())
        .progressPercentage(deploymentVerificationJobInstanceSummary.getProgressPercentage())
        .activityId(activityId)
        .status(deploymentVerificationJobInstanceSummary.getStatus())
        .build();
  }

  @Override
  public String getDeploymentTagFromActivity(String accountId, String verificationJobInstanceId) {
    DeploymentActivity deploymentActivity =
        (DeploymentActivity) hPersistence.createQuery(Activity.class, excludeAuthority)
            .filter(ActivityKeys.accountId, accountId)
            .filter(ActivityKeys.type, ActivityType.DEPLOYMENT)
            .field(ActivityKeys.verificationJobInstanceIds)
            .hasThisOne(verificationJobInstanceId)
            .get();
    if (deploymentActivity != null) {
      return deploymentActivity.getDeploymentTag();
    } else {
      throw new IllegalStateException("Activity not found for verificationJobInstanceId: " + verificationJobInstanceId);
    }
  }

  @Override
  public List<LogAnalysisClusterChartDTO> getDeploymentActivityLogAnalysisClusters(
      String accountId, String activityId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    List<String> verificationJobInstanceIds = getVerificationJobInstanceId(activityId);
    // TODO: We currently support only one verificationJobInstance per deployment. Hence this check. Revisit if that
    // changes later
    Preconditions.checkState(verificationJobInstanceIds.size() == 1,
        "We do not support more than one monitored source validation from deployment");
    return deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, verificationJobInstanceIds.get(0), deploymentLogAnalysisFilter);
  }

  @Override
  public PageResponse<LogAnalysisClusterDTO> getDeploymentActivityLogAnalysisResult(String accountId, String activityId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams) {
    List<String> verificationJobInstanceIds = getVerificationJobInstanceId(activityId);
    // TODO: We currently support only one verificationJobInstance per deployment. Hence this check. Revisit if that
    // changes later
    Preconditions.checkState(verificationJobInstanceIds.size() == 1,
        "We do not support more than one monitored source validation from deployment");
    return deploymentLogAnalysisService.getLogAnalysisResult(
        accountId, verificationJobInstanceIds.get(0), label, deploymentLogAnalysisFilter, pageParams);
  }

  @Override
  public void abort(String activityId) {
    Activity activity = hPersistence.createQuery(Activity.class)
                            .filter(ActivityKeys.uuid, activityId)
                            .field(ActivityKeys.analysisStatus)
                            .notIn(ActivityVerificationStatus.getFinalStates())
                            .get();
    if (activity != null) {
      verificationJobInstanceService.abort(activity.getVerificationJobInstanceIds());
    }
  }

  @Override
  public Set<HealthSourceDTO> healthSources(String accountId, String activityId) {
    Set<HealthSourceDTO> healthSourceDTOS = new HashSet<>();
    List<VerificationJobInstance> verificationJobInstances =
        verificationJobInstanceService.get(getVerificationJobInstanceId(activityId));
    verificationJobInstances.forEach(verificationJobInstance -> {
      verificationJobInstance.getCvConfigMap().forEach((s, cvConfig) -> {
        HealthSourceDTO healthSourceDTO = HealthSourceDTO.toHealthSourceDTO(
            HealthSourceDTO.toHealthSource(Arrays.asList(cvConfig), dataSourceTypeToHealthSourceTransformerMap));
        healthSourceDTO.setIdentifier(cvConfig.getFullyQualifiedIdentifier());
        healthSourceDTOS.add(healthSourceDTO);
      });
    });
    return healthSourceDTOS;
  }

  @Override
  public Optional<Activity> getAnyKubernetesEvent(
      MonitoredServiceParams monitoredServiceParams, Instant startTime, Instant endTime) {
    return Optional.ofNullable(createQuery(monitoredServiceParams)
                                   .filter(ActivityKeys.type, ActivityType.KUBERNETES)
                                   .field(ActivityKeys.activityStartTime)
                                   .greaterThanOrEq(startTime)
                                   .field(ActivityKeys.activityStartTime)
                                   .lessThan(endTime)
                                   .get());
  }

  @Override
  public Optional<Activity> getAnyEventFromListOfActivityTypes(MonitoredServiceParams monitoredServiceParams,
      List<ActivityType> activityTypes, Instant startTime, Instant endTime) {
    return Optional.ofNullable(createQuery(monitoredServiceParams)
                                   .field(ActivityKeys.type)
                                   .in(activityTypes)
                                   .field(ActivityKeys.activityStartTime)
                                   .greaterThanOrEq(startTime)
                                   .field(ActivityKeys.activityStartTime)
                                   .lessThan(endTime)
                                   .order(Sort.descending(ActivityKeys.activityStartTime))
                                   .get());
  }

  @Override
  public Optional<Activity> getAnyDemoDeploymentEvent(MonitoredServiceParams monitoredServiceParams, Instant startTime,
      Instant endTime, ActivityVerificationStatus verificationStatus) {
    return Optional.ofNullable(createQuery(monitoredServiceParams)
                                   .filter(ActivityKeys.type, ActivityType.DEPLOYMENT)
                                   .field(ActivityKeys.activityStartTime)
                                   .greaterThanOrEq(startTime)
                                   .field(ActivityKeys.activityStartTime)
                                   .lessThan(endTime)
                                   .filter(DeploymentActivityKeys.isDemoActivity, true)
                                   .filter(ActivityKeys.analysisStatus, verificationStatus)
                                   .get());
  }

  private List<String> getVerificationJobInstanceId(String activityId) {
    Preconditions.checkNotNull(activityId);
    CVNGStepTask cvngStepTask = cvngStepTaskService.getByCallBackId(activityId);
    if (cvngStepTask != null && StringUtils.isBlank(cvngStepTask.getVerificationJobInstanceId())) {
      Activity activity = get(activityId);
      Preconditions.checkNotNull(activity, String.format("Activity does not exists with activityID %s", activityId));
      return activity.getVerificationJobInstanceIds();
    } else {
      return Arrays.asList(cvngStepTask.getVerificationJobInstanceId());
    }
  }

  private void validateJob(VerificationJob verificationJob) {
    List<CVConfig> cvConfigs = verificationJobInstanceService.getCVConfigsForVerificationJob(verificationJob);
    Preconditions.checkState(isNotEmpty(cvConfigs),
        "No monitoring sources with identifiers %s defined for environment %s and service %s",
        verificationJob.getMonitoringSources(), verificationJob.getEnvIdentifier(),
        verificationJob.getServiceIdentifier());
  }

  private VerificationJobInstanceBuilder fillOutCommonJobInstanceProperties(
      Activity activity, VerificationJob verificationJob) {
    return VerificationJobInstance.builder()
        .accountId(activity.getAccountId())
        .executionStatus(ExecutionStatus.QUEUED)
        .deploymentStartTime(activity.getActivityStartTime())
        .resolvedJob(verificationJob);
  }

  @Override
  public String upsert(Activity activity) {
    ActivityUpdateHandler handler = activityUpdateHandlerMap.get(activity.getType());
    ActivityUpdatableEntity activityUpdatableEntity = activityUpdatableEntityMap.get(activity.getType());
    try (AcquiredLock acquiredLock = persistentLocker.waitToAcquireLock(Activity.class,
             activityUpdatableEntity.getEntityKeyString(activity), Duration.ofSeconds(6), Duration.ofSeconds(4))) {
      Optional<Activity> optionalFromDb =
          StringUtils.isEmpty(activity.getUuid()) ? getFromDb(activity) : Optional.ofNullable(get(activity.getUuid()));
      if (optionalFromDb.isPresent()) {
        UpdateOperations<Activity> updateOperations =
            hPersistence.createUpdateOperations(activityUpdatableEntity.getEntityClass());
        activityUpdatableEntity.setUpdateOperations(updateOperations, activity);
        if (handler != null) {
          handler.handleUpdate(optionalFromDb.get(), activity);
        }
        hPersistence.update(optionalFromDb.get(), updateOperations);
        return optionalFromDb.get().getUuid();
      } else {
        if (handler != null) {
          handler.handleCreate(activity);
        }
        hPersistence.save(activity);
      }
      log.info("Registered  an activity of type {} for account {}, project {}, org {}", activity.getType(),
          activity.getAccountId(), activity.getProjectIdentifier(), activity.getOrgIdentifier());
      return activity.getUuid();
    }
  }

  private Query<Activity> createQuery(MonitoredServiceParams monitoredServiceParams) {
    return hPersistence.createQuery(Activity.class, excludeValidate)
        .filter(ActivityKeys.accountId, monitoredServiceParams.getAccountIdentifier())
        .filter(ActivityKeys.orgIdentifier, monitoredServiceParams.getOrgIdentifier())
        .filter(ActivityKeys.projectIdentifier, monitoredServiceParams.getProjectIdentifier())
        .filter(ActivityKeys.monitoredServiceIdentifier, monitoredServiceParams.getMonitoredServiceIdentifier());
  }

  private Optional<Activity> getFromDb(Activity activity) {
    ActivityUpdatableEntity activityUpdatableEntity = activityUpdatableEntityMap.get(activity.getType());
    return Optional.ofNullable(
        (Activity) activityUpdatableEntity
            .populateKeyQuery(hPersistence.createQuery(activityUpdatableEntity.getEntityClass()), activity)
            .get());
  }
}
