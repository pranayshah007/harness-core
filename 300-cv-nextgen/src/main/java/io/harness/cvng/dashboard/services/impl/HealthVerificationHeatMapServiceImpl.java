/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import static org.mongodb.morphia.aggregation.Accumulator.accumulator;
import static org.mongodb.morphia.aggregation.Group.grouping;
import static org.mongodb.morphia.aggregation.Group.id;
import static org.mongodb.morphia.aggregation.Projection.projection;

import io.harness.cvng.activity.beans.ActivityVerificationResultDTO.CategoryRisk;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.entities.HealthVerificationHeatMap;
import io.harness.cvng.dashboard.entities.HealthVerificationHeatMap.AggregationLevel;
import io.harness.cvng.dashboard.entities.HealthVerificationHeatMap.HealthVerificationHeatMapKeys;
import io.harness.cvng.dashboard.services.api.HealthVerificationHeatMapService;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class HealthVerificationHeatMapServiceImpl implements HealthVerificationHeatMapService {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private ActivityService activityService;

  @Override
  public void updateRisk(String verificationTaskId, Double overallRisk, Instant endTime,
      HealthVerificationPeriod healthVerificationPeriod) {
    // Update the risk score for that specific verificationTaskId and for the activity level as well.
    Preconditions.checkNotNull(verificationTaskId, "verificationTaskId should not be null");
    VerificationTask verificationTask = verificationTaskService.get(verificationTaskId);
    Preconditions.checkNotNull(verificationTask.getTaskInfo().getTaskType().equals(TaskType.DEPLOYMENT),
        "VerificationTask should be of Deployment type");
    CVConfig cvConfig = verificationJobInstanceService.getEmbeddedCVConfig(
        ((DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId(),
        ((DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId());
    Activity activity = activityService.getByVerificationJobInstanceId(
        ((DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId());

    updateRiskScoreInDB(verificationTaskId, AggregationLevel.VERIFICATION_TASK, healthVerificationPeriod, cvConfig,
        activity, overallRisk, endTime);
    updateActivityRiskScore(verificationTaskId, overallRisk, cvConfig, activity, healthVerificationPeriod, endTime);
    log.info("Updated the health verification risk score for verificationTaskId {}, category {}, period {} to {}",
        verificationTaskId, cvConfig.getCategory(), healthVerificationPeriod, overallRisk);
  }

  @Override
  public Optional<Risk> getVerificationRisk(String accountId, String verificationJobInstanceId) {
    Set<String> taskIds = verificationTaskService.getVerificationTaskIds(accountId, verificationJobInstanceId);
    List<Double> risks = new ArrayList<>();
    Query<HealthVerificationHeatMap> heatMapQuery =
        hPersistence.createQuery(HealthVerificationHeatMap.class, excludeAuthority)
            .field(HealthVerificationHeatMapKeys.aggregationId)
            .in(taskIds)
            .filter(HealthVerificationHeatMapKeys.aggregationLevel,
                HealthVerificationHeatMap.AggregationLevel.VERIFICATION_TASK)
            .filter(HealthVerificationHeatMapKeys.healthVerificationPeriod, HealthVerificationPeriod.POST_ACTIVITY);

    List<HealthVerificationHeatMap> heatMaps = heatMapQuery.asList();

    heatMaps.forEach(heatMap -> risks.add(heatMap.getRiskScore()));

    if (isNotEmpty(risks)) {
      double max = Collections.max(risks);
      return Optional.of(Risk.getRiskFromRiskScore(max));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Set<CategoryRisk> getVerificationJobInstanceAggregatedRisk(
      String accountId, String verificationJobInstanceId, HealthVerificationPeriod healthVerificationPeriod) {
    Preconditions.checkNotNull(
        verificationJobInstanceId, "verificationJobInstanceId is null when trying to get aggregated risk");
    Map<CVMonitoringCategory, Integer> scoreMap = new HashMap<>();
    Set<CategoryRisk> categoryRisks = new HashSet<>();
    Set<String> verificationTaskIds =
        verificationTaskService.maybeGetVerificationTaskIds(accountId, verificationJobInstanceId);
    List<HealthVerificationHeatMap> heatMaps =
        hPersistence.createQuery(HealthVerificationHeatMap.class, excludeAuthority)
            .field(HealthVerificationHeatMapKeys.aggregationId)
            .in(verificationTaskIds)
            .filter(HealthVerificationHeatMapKeys.healthVerificationPeriod, healthVerificationPeriod)
            .filter(HealthVerificationHeatMapKeys.aggregationLevel, AggregationLevel.VERIFICATION_TASK)
            .asList();
    heatMaps.forEach(heatMap -> {
      Double risk = heatMap.getRiskScore() * 100;
      scoreMap.put(heatMap.getCategory(),
          Math.max(scoreMap.getOrDefault(heatMap.getCategory(), Integer.MIN_VALUE), risk.intValue()));
    });

    Arrays.asList(CVMonitoringCategory.values()).forEach(category -> {
      if (!scoreMap.containsKey(category)) {
        categoryRisks.add(CategoryRisk.builder().category(category).risk(-1.0).build());
      } else {
        categoryRisks.add(
            CategoryRisk.builder().category(category).risk(Double.valueOf(scoreMap.get(category))).build());
      }
    });

    return categoryRisks;
  }

  private void updateActivityRiskScore(String verificationTaskId, Double overallRisk, CVConfig cvConfig,
      Activity activity, HealthVerificationPeriod healthVerificationPeriod, Instant endTime) {
    CVMonitoringCategory category = cvConfig.getCategory();

    // get the latest risk for each verifiationTaskId and then aggregate the max.
    Query<HealthVerificationHeatMap> heatMapQuery =
        hPersistence.createQuery(HealthVerificationHeatMap.class, excludeAuthority)
            .filter(HealthVerificationHeatMapKeys.category, category)
            .filter(HealthVerificationHeatMapKeys.aggregationLevel,
                HealthVerificationHeatMap.AggregationLevel.VERIFICATION_TASK)
            .filter(HealthVerificationHeatMapKeys.activityId, activity.getUuid());

    List<Double> allRisks = new ArrayList<>();
    hPersistence.getDatastore(HealthVerificationHeatMap.class)
        .createAggregation(HealthVerificationHeatMap.class)
        .match(heatMapQuery)
        .project(projection(HealthVerificationHeatMapKeys.riskScore), projection(HealthVerificationHeatMapKeys.endTime),
            projection(HealthVerificationHeatMapKeys.aggregationId), projection(HealthVerificationHeatMapKeys.category))
        .group(id(grouping(HealthVerificationHeatMapKeys.aggregationId)),
            grouping(
                HealthVerificationHeatMapKeys.endTime, accumulator("$last", HealthVerificationHeatMapKeys.endTime)),
            grouping(
                HealthVerificationHeatMapKeys.riskScore, accumulator("$last", HealthVerificationHeatMapKeys.riskScore)))
        .aggregate(HealthVerificationHeatMap.class)
        .forEachRemaining(healthVerificationHeatMap -> { allRisks.add(healthVerificationHeatMap.getRiskScore()); });
    if (isEmpty(allRisks) || overallRisk >= Collections.max(allRisks)) {
      // update the activityLevel riskScore
      updateRiskScoreInDB(activity.getUuid(), AggregationLevel.ACTIVITY, healthVerificationPeriod, cvConfig, activity,
          overallRisk, endTime);
      log.info("Updated the activity risk score for activity {}, category {}, period {} to {}", activity.getUuid(),
          category, healthVerificationPeriod, overallRisk);
    }
  }

  private void updateRiskScoreInDB(String aggregationId, AggregationLevel aggregationLevel,
      HealthVerificationPeriod healthVerificationPeriod, CVConfig cvConfig, Activity activity, Double overallRisk,
      Instant endTime) {
    Query<HealthVerificationHeatMap> heatMapQuery =
        hPersistence.createQuery(HealthVerificationHeatMap.class, excludeAuthority)
            .filter(HealthVerificationHeatMapKeys.aggregationId, aggregationId)
            .filter(HealthVerificationHeatMapKeys.healthVerificationPeriod, healthVerificationPeriod)
            .filter(HealthVerificationHeatMapKeys.aggregationLevel, aggregationLevel)
            .filter(HealthVerificationHeatMapKeys.category, cvConfig.getCategory());

    UpdateOperations<HealthVerificationHeatMap> heatMapUpdateOperations =
        hPersistence.createUpdateOperations(HealthVerificationHeatMap.class)
            .setOnInsert(HealthVerificationHeatMapKeys.uuid, generateUuid())
            .setOnInsert(HealthVerificationHeatMapKeys.aggregationId, aggregationId)
            .setOnInsert(HealthVerificationHeatMapKeys.category, cvConfig.getCategory())
            .setOnInsert(HealthVerificationHeatMapKeys.projectIdentifier, cvConfig.getProjectIdentifier())
            .setOnInsert(HealthVerificationHeatMapKeys.accountId, cvConfig.getAccountId())
            .setOnInsert(HealthVerificationHeatMapKeys.healthVerificationPeriod, healthVerificationPeriod)
            .setOnInsert(HealthVerificationHeatMapKeys.activityId, activity.getUuid())
            .setOnInsert(HealthVerificationHeatMapKeys.aggregationLevel, aggregationLevel)
            .setOnInsert(
                HealthVerificationHeatMapKeys.startTime, activity.getActivityStartTime().minus(30, ChronoUnit.MINUTES))
            .setOnInsert(
                HealthVerificationHeatMapKeys.validUntil, HealthVerificationHeatMap.builder().build().getValidUntil())
            .set(HealthVerificationHeatMapKeys.endTime, endTime)
            .set(HealthVerificationHeatMapKeys.riskScore, overallRisk);

    hPersistence.upsert(heatMapQuery, heatMapUpdateOperations);
  }
}
