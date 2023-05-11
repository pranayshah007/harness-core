/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.activity.beans.ActivityVerificationSummary;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.entities.Activity.ActivityUpdatableEntity;
import io.harness.cvng.activity.entities.ActivityBucket;
import io.harness.cvng.activity.entities.ActivityBucket.ActivityBucketKeys;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityKeys;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventEntityAndDTOTransformer;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.ReadPreference;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class ActivityServiceImpl implements ActivityService {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private Map<ActivityType, ActivityUpdatableEntity> activityUpdatableEntityMap;
  @Inject private Map<ActivityType, ActivityUpdateHandler> activityUpdateHandlerMap;
  @Inject private PersistentLocker persistentLocker;
  @Inject ChangeEventEntityAndDTOTransformer transformer;

  @Override
  public Activity get(String activityId) {
    Preconditions.checkNotNull(activityId, "ActivityID should not be null");
    return hPersistence.get(Activity.class, activityId);
  }

  @Override
  public List<Activity> getByMonitoredServiceIdentifier(MonitoredServiceParams monitoredServiceParams) {
    return createQuery(monitoredServiceParams).asList();
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

  @Override
  public String getDeploymentTagFromActivity(String accountId, String verificationJobInstanceId) {
    DeploymentActivity deploymentActivity =
        (DeploymentActivity) hPersistence.createQuery(Activity.class, excludeAuthority)
            .useReadPreference(ReadPreference.secondaryPreferred())
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
        saveActivityBucket(activity);
      }
      log.info("Registered an activity of type {} for account {}, project {}, org {}", activity.getType(),
          activity.getAccountId(), activity.getProjectIdentifier(), activity.getOrgIdentifier());
      return activity.getUuid();
    }
  }

  @Override
  public void saveActivityBucket(Activity activity) {
    ActivityBucket activityBucket = transformer.getActivityBucket(activity);
    Query<ActivityBucket> upsertActivityBucket =
        hPersistence.createQuery(ActivityBucket.class)
            .filter(ActivityBucketKeys.accountId, activityBucket.getAccountId())
            .filter(ActivityBucketKeys.orgIdentifier, activityBucket.getOrgIdentifier())
            .filter(ActivityBucketKeys.projectIdentifier, activityBucket.getProjectIdentifier())
            .filter(ActivityBucketKeys.monitoredServiceIdentifiers, activityBucket.getMonitoredServiceIdentifiers())
            .filter(ActivityBucketKeys.type, activityBucket.getType())
            .filter(ActivityKeys.validUntil, activityBucket.getBucketTime())
            .filter(ActivityBucketKeys.bucketTime, activityBucket.getBucketTime());
    UpdateOperations<ActivityBucket> updateOperations = hPersistence.createUpdateOperations(ActivityBucket.class);
    updateOperations.inc(ActivityBucketKeys.count);
    FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions().upsert(true);
    hPersistence.upsert(upsertActivityBucket, updateOperations, findAndModifyOptions);
  }

  @Override
  public boolean deleteByMonitoredServiceIdentifier(MonitoredServiceParams monitoredServiceParams) {
    return hPersistence.delete(createQuery(monitoredServiceParams));
  }

  private Query<Activity> createQuery(MonitoredServiceParams monitoredServiceParams) {
    Query<Activity> query =
        hPersistence.createQuery(Activity.class, excludeValidate)
            .filter(ActivityKeys.accountId, monitoredServiceParams.getAccountIdentifier())
            .filter(ActivityKeys.orgIdentifier, monitoredServiceParams.getOrgIdentifier())
            .filter(ActivityKeys.projectIdentifier, monitoredServiceParams.getProjectIdentifier())
            .filter(ActivityKeys.monitoredServiceIdentifier, monitoredServiceParams.getMonitoredServiceIdentifier());
    query.useReadPreference(ReadPreference.secondaryPreferred());
    return query;
  }

  private Optional<Activity> getFromDb(Activity activity) {
    ActivityUpdatableEntity activityUpdatableEntity = activityUpdatableEntityMap.get(activity.getType());
    return Optional.ofNullable(
        (Activity) activityUpdatableEntity
            .populateKeyQuery(hPersistence.createQuery(activityUpdatableEntity.getEntityClass()), activity)
            .get());
  }
}
