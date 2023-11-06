/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.core.utils.DateTimeUtils.roundUpTo5MinBoundary;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MS_HEALTH_REPORT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.QueryChecks.COUNT;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Group.id;

import io.harness.beans.FeatureName;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.entities.ActivityBucket;
import io.harness.cvng.activity.entities.ActivityBucket.ActivityBucketKeys;
import io.harness.cvng.activity.entities.CustomChangeActivity.CustomChangeActivityKeys;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityKeys;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.KubernetesClusterActivityKeys;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.RelatedAppMonitoredService.ServiceEnvironmentKeys;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.SRMAnalysisStepDetailDTO;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.CustomChangeEvent;
import io.harness.cvng.beans.change.CustomChangeEventMetadata;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO.CategoryCountDetails;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.change.ChangeTimeline.ChangeTimelineBuilder;
import io.harness.cvng.core.beans.change.ChangeTimeline.TimeRangeDetail;
import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ResourceParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MSHealthReportService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventEntityAndDTOTransformer;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.FireHydrantReportNotificationCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.utils.MathUtils;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.AggregationOptions;
import com.mongodb.ReadPreference;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.annotations.Id;
import dev.morphia.query.Criteria;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

// TODO: merge ChangeEventService and ActivityService
public class ChangeEventServiceImpl implements ChangeEventService {
  @Inject ChangeSourceService changeSourceService;
  @Inject ChangeEventEntityAndDTOTransformer transformer;
  @Inject ActivityService activityService;
  @Inject HPersistence hPersistence;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject MSHealthReportService msHealthReportService;
  @Inject FeatureFlagService featureFlagService;

  @Inject SRMAnalysisStepService srmAnalysisStepService;

  @Inject Clock clock;

  private String SLACK_WEBHOOK_URL = "https://slack.com/api/chat.postMessage";

  @Override
  public Boolean register(ChangeEventDTO changeEventDTO) {
    if (isEmpty(changeEventDTO.getMonitoredServiceIdentifier())) {
      Optional<MonitoredService> monitoredService = monitoredServiceService.getApplicationMonitoredService(
          ServiceEnvironmentParams.builder()
              .accountIdentifier(changeEventDTO.getAccountId())
              .orgIdentifier(changeEventDTO.getOrgIdentifier())
              .projectIdentifier(changeEventDTO.getProjectIdentifier())
              .serviceIdentifier(changeEventDTO.getServiceIdentifier())
              .environmentIdentifier(changeEventDTO.getEnvIdentifier())
              .build());
      if (monitoredService.isPresent()) {
        changeEventDTO.setMonitoredServiceIdentifier(monitoredService.get().getIdentifier());
      } else {
        return false;
      }
    }
    Preconditions.checkNotNull(
        changeEventDTO.getMonitoredServiceIdentifier(), "Monitored service identifier should not be null");
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(changeEventDTO.getAccountId())
            .orgIdentifier(changeEventDTO.getOrgIdentifier())
            .projectIdentifier(changeEventDTO.getProjectIdentifier())
            .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
            .build();

    if (changeEventDTO.getMetadata().getType().isInternal()) {
      String activityId = activityService.upsert(transformer.getEntity(changeEventDTO));
      if (changeEventDTO.getMetadata().getType().equals(ChangeSourceType.HARNESS_CD)) {
        mapSRMAnalysisExecutionsToDeploymentActivities(activityId);
      }
      return true;
    }
    Optional<ChangeSource> changeSourceOptional =
        changeSourceService.getEntityByType(monitoredServiceParams, changeEventDTO.getType())
            .stream()
            .filter(ChangeSource::isEnabled)
            .findAny();
    if (changeSourceOptional.isEmpty()) {
      return false;
    }
    if (StringUtils.isEmpty(changeEventDTO.getChangeSourceIdentifier())) {
      changeEventDTO.setChangeSourceIdentifier(changeSourceOptional.get().getIdentifier());
    }
    activityService.upsert(transformer.getEntity(changeEventDTO));
    return true;
  }

  @Override
  public Boolean registerWithHealthReport(ChangeEventDTO changeEventDTO, String webhookUrl, String authorizationToken) {
    register(changeEventDTO);

    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(changeEventDTO.getAccountId())
                                      .orgIdentifier(changeEventDTO.getOrgIdentifier())
                                      .projectIdentifier(changeEventDTO.getProjectIdentifier())
                                      .build();
    MSHealthReport msHealthReport = msHealthReportService.getMSHealthReport(
        projectParams, changeEventDTO.getMonitoredServiceIdentifier(), clock.instant().minus(1, ChronoUnit.HOURS));
    CustomChangeEvent customChangeEvent =
        ((CustomChangeEventMetadata) changeEventDTO.getMetadata()).getCustomChangeEvent();
    ((CustomChangeEventMetadata) changeEventDTO.getMetadata()).setCustomChangeEvent(customChangeEvent);

    activityService.upsert(transformer.getEntity(changeEventDTO));
    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builderWithProjectParams(projectParams)
            .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
            .build());

    String channelId = ((CustomChangeEventMetadata) changeEventDTO.getMetadata()).getCustomChangeEvent().getChannelId();

    Map<String, Object> entityDetails = new HashMap(Map.of(ENTITY_IDENTIFIER,
        changeEventDTO.getMonitoredServiceIdentifier(), ENTITY_NAME, monitoredService.getName(), SERVICE_IDENTIFIER,
        monitoredService.getServiceIdentifier(), MS_HEALTH_REPORT, msHealthReport));

    if (isNotEmpty(channelId) && isNotEmpty(authorizationToken)) {
      entityDetails.put("CHANNEL_ID", channelId);
      msHealthReportService.sendReportNotification(projectParams, entityDetails, NotificationRuleType.FIRE_HYDRANT,
          FireHydrantReportNotificationCondition.builder().build(),
          new NotificationRule.CVNGWebhookChannel(null, SLACK_WEBHOOK_URL, authorizationToken),
          changeEventDTO.getMonitoredServiceIdentifier());
    } else {
      msHealthReportService.sendReportNotification(projectParams, entityDetails, NotificationRuleType.FIRE_HYDRANT,
          FireHydrantReportNotificationCondition.builder().build(),
          new NotificationRule.CVNGSlackChannel(null, webhookUrl), changeEventDTO.getMonitoredServiceIdentifier());
    }
    return true;
  }

  @Override
  public ChangeEventDTO get(String activityId) {
    return transformer.getDto(activityService.get(activityId));
  }

  @Override
  public ChangeSummaryDTO getChangeSummary(MonitoredServiceParams monitoredServiceParams,
      List<String> changeSourceIdentifiers, Instant startTime, Instant endTime) {
    return getChangeSummary(monitoredServiceParams,
        Collections.singletonList(monitoredServiceParams.getMonitoredServiceIdentifier()), null, null, startTime,
        endTime, false);
  }

  @Override
  public PageResponse<ChangeEventDTO> getChangeEvents(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifiers, List<String> monitoredServiceIdentifiers,
      boolean isMonitoredServiceIdentifierScoped, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, PageRequest pageRequest) {
    if (isNotEmpty(monitoredServiceIdentifiers)) {
      Preconditions.checkState(isEmpty(serviceIdentifiers) && isEmpty(environmentIdentifiers),
          "serviceIdentifier, envIdentifier filter can not be used with monitoredServiceIdentifier filter");
      return getChangeEvents(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime,
          endTime, pageRequest, isMonitoredServiceIdentifierScoped);
    } else {
      return getChangeEvents(projectParams, serviceIdentifiers, environmentIdentifiers, changeCategories,
          changeSourceTypes, startTime, endTime, pageRequest);
    }
  }

  @Override
  public PageResponse<ChangeEventDTO> getChangeEvents(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, PageRequest pageRequest) {
    List<String> monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
        projectParams, serviceIdentifiers, environmentIdentifiers);
    return getChangeEvents(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime,
        endTime, pageRequest, false);
  }

  private PageResponse<ChangeEventDTO> getChangeEvents(ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, PageRequest pageRequest,
      boolean isMonitoredServiceIdentifierScoped) {
    Query<Activity> query = createQuery(startTime, endTime, projectParams, monitoredServiceIdentifiers,
        changeCategories, changeSourceTypes, isMonitoredServiceIdentifierScoped)
                                .order(Sort.descending(ActivityKeys.eventTime));
    List<Activity> activities;
    if (featureFlagService.isFeatureFlagEnabled(
            projectParams.getAccountIdentifier(), FeatureName.SRM_OPTIMISE_CHANGE_EVENTS_API_RESPONSE.name())) {
      long count = query.count();
      query = setProjectionsToFetchNecessaryFields(query)
                  .offset(pageRequest.getPageIndex() * pageRequest.getPageSize())
                  .limit(pageRequest.getPageSize());
      activities = query.asList();
      return PageUtils.offsetAndLimit(activities.stream().map(transformer::getDto).collect(Collectors.toList()), count,
          pageRequest.getPageIndex(), pageRequest.getPageSize());
    }
    activities = query.asList();
    return PageUtils.offsetAndLimit(activities.stream().map(transformer::getDto).collect(Collectors.toList()),
        pageRequest.getPageIndex(), pageRequest.getPageSize());
  }

  private static Query<Activity> setProjectionsToFetchNecessaryFields(Query<Activity> query) {
    return query.project(ActivityKeys.uuid, true)
        .project(ActivityKeys.accountId, true)
        .project(ActivityKeys.orgIdentifier, true)
        .project(ActivityKeys.projectIdentifier, true)
        .project(ActivityKeys.monitoredServiceIdentifier, true)
        .project(ActivityKeys.eventTime, true)
        .project(ActivityKeys.activityName, true)
        .project(ActivityKeys.type, true)
        .project(ActivityKeys.changeSourceIdentifier, true)
        .project(ActivityKeys.activityStartTime, true)
        .project(ActivityKeys.activityEndTime, true)
        .project(DeploymentActivityKeys.runSequence, true)
        .project(DeploymentActivityKeys.pipelineId, true)
        .project(CustomChangeActivityKeys.activityType, true);
  }

  public PageResponse<SRMAnalysisStepDetailDTO> getReportList(ProjectParams projectParams,
      List<String> serviceIdentifiers, List<String> environmentIdentifier, List<String> monitoredServiceIdentifiers,
      boolean isMonitoredServiceIdentifierScoped, Instant startTime, Instant endTime, PageRequest pageRequest) {
    return srmAnalysisStepService.getReportList(projectParams, serviceIdentifiers, environmentIdentifier,
        monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped, startTime, endTime, pageRequest);
  }

  private ChangeTimeline getTimeline(ProjectParams projectParams, List<String> monitoredServiceIdentifiers,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime, Integer pointCount, boolean isMonitoredServiceIdentifierScoped) {
    Map<ChangeCategory, Map<Integer, TimeRangeDetail>> categoryMilliSecondFromStartDetailMap =
        Arrays.stream(ChangeCategory.values())
            .collect(Collectors.toMap(Function.identity(), c -> new HashMap<>(), (u, v) -> u, LinkedHashMap::new));

    Duration timeRangeDuration = Duration.between(startTime, endTime).dividedBy(pointCount);

    getTimelineObject(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime,
        endTime, pointCount, isMonitoredServiceIdentifierScoped)
        .forEachRemaining(timelineObject -> {
          ChangeCategory changeCategory = ChangeSourceType.ofActivityType(timelineObject.id.type).getChangeCategory();
          Map<Integer, TimeRangeDetail> milliSecondFromStartDetailMap =
              categoryMilliSecondFromStartDetailMap.getOrDefault(changeCategory, new HashMap<>());
          categoryMilliSecondFromStartDetailMap.put(changeCategory, milliSecondFromStartDetailMap);
          TimeRangeDetail timeRangeDetail = milliSecondFromStartDetailMap.getOrDefault(timelineObject.id.index,
              TimeRangeDetail.builder()
                  .count(0L)
                  .startTime(startTime.plus(timeRangeDuration.multipliedBy(timelineObject.id.index)).toEpochMilli())
                  .endTime(startTime.plus(timeRangeDuration.multipliedBy(timelineObject.id.index))
                               .plus(timeRangeDuration)
                               .toEpochMilli())
                  .build());
          timeRangeDetail.incrementCount(timelineObject.count);
          milliSecondFromStartDetailMap.put(timelineObject.id.index, timeRangeDetail);
        });

    ChangeTimelineBuilder changeTimelineBuilder = ChangeTimeline.builder();
    categoryMilliSecondFromStartDetailMap.forEach(
        (key, value) -> changeTimelineBuilder.categoryTimeline(key, new ArrayList<>(value.values())));
    return changeTimelineBuilder.build();
  }

  @Override
  public ChangeTimeline getTimeline(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifiers, List<String> monitoredServiceIdentifiers,
      boolean isMonitoredServiceIdentifierScoped, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, Integer pointCount) {
    startTime = roundDownTo5MinBoundary(startTime);
    endTime = roundUpTo5MinBoundary(endTime);
    if (isNotEmpty(monitoredServiceIdentifiers)) {
      Preconditions.checkState(isEmpty(serviceIdentifiers) && isEmpty(environmentIdentifiers),
          "serviceIdentifier, envIdentifier filter can not be used with monitoredServiceIdentifier filter");
    } else {
      monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
          projectParams, serviceIdentifiers, environmentIdentifiers);
    }
    return getTimeline(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime,
        endTime, pointCount, isMonitoredServiceIdentifierScoped);
  }

  @Override
  public ChangeTimeline getMonitoredServiceChangeTimeline(MonitoredServiceParams monitoredServiceParams,
      List<ChangeSourceType> changeSourceTypes, DurationDTO duration, Instant endTime) {
    HeatMapResolution resolution = HeatMapResolution.resolutionForDurationDTO(duration);
    Instant trendEndTime = resolution.getNextResolutionEndTime(endTime);
    Instant trendStartTime = trendEndTime.minus(duration.getDuration());
    String monitoredServiceIdentifier = monitoredServiceParams.getMonitoredServiceIdentifier();
    Preconditions.checkNotNull(monitoredServiceIdentifier, "monitoredServiceIdentifier can not be null");
    return getTimeline(monitoredServiceParams, List.of(monitoredServiceIdentifier), null, changeSourceTypes,
        trendStartTime, trendEndTime, CVNextGenConstants.CVNG_TIMELINE_BUCKET_COUNT, false);
  }

  private Iterator<TimelineObject> getTimelineObject(ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, Integer pointCount,
      boolean isMonitoredServiceIdentifierScoped) {
    Duration timeRangeDuration = Duration.between(startTime, endTime).dividedBy(pointCount);
    AggregationPipeline aggregationPipeline =
        hPersistence.getDatastore(ActivityBucket.class)
            .createAggregation(ActivityBucket.class)
            .match(createQueryForActivityBucket(startTime, endTime, projectParams, monitoredServiceIdentifiers,
                changeCategories, changeSourceTypes, isMonitoredServiceIdentifierScoped))
            .group(id(grouping("type", "type"),
                       grouping("index",
                           accumulator("$floor",
                               accumulator("$divide",
                                   Arrays.asList(accumulator("$subtract",
                                                     Arrays.asList("$bucketTime", new Date(startTime.toEpochMilli()))),
                                       timeRangeDuration.toMillis()))))),
                grouping("count", accumulator("$sum", "count")));
    int limit = hPersistence.getMaxDocumentLimit(ActivityBucket.class);
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    return aggregationPipeline.aggregate(TimelineObject.class,
        AggregationOptions.builder()
            .maxTime(hPersistence.getMaxTimeMs(ActivityBucket.class), TimeUnit.MILLISECONDS)
            .build());
  }
  @VisibleForTesting
  Iterator<TimelineObject> getTimelineObject(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifier, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, Integer pointCount,
      boolean isMonitoredServiceIdentifierScoped) {
    List<String> monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
        projectParams, serviceIdentifiers, environmentIdentifier);
    return getTimelineObject(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime,
        endTime, pointCount, isMonitoredServiceIdentifierScoped);
  }

  @Override
  public ChangeSummaryDTO getChangeSummary(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime) {
    List<String> monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
        projectParams, serviceIdentifiers, environmentIdentifiers);
    return getChangeSummary(
        projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime, endTime, false);
  }

  @Override
  public ChangeSummaryDTO getChangeSummary(ProjectParams projectParams, String monitoredServiceIdentifier,
      List<String> monitoredServiceIdentifiers, boolean isMonitoredServiceIdentifierScoped,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime) {
    if (isEmpty(monitoredServiceIdentifiers)) {
      monitoredServiceIdentifiers = Collections.singletonList(monitoredServiceIdentifier);
    }
    return getChangeSummary(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime,
        endTime, isMonitoredServiceIdentifierScoped);
  }

  @Override
  public void mapSRMAnalysisExecutionsToDeploymentActivities(SRMAnalysisStepExecutionDetail stepExecutionDetail) {
    List<DeploymentActivity> deploymentActivities =
        getAnalysisStepAssociatedDeploymentActivities(stepExecutionDetail.getAccountId(),
            stepExecutionDetail.getOrgIdentifier(), stepExecutionDetail.getProjectIdentifier(),
            stepExecutionDetail.getPlanExecutionId(), stepExecutionDetail.getStageId());
    if (isNotEmpty(deploymentActivities)) {
      deploymentActivities.forEach(activity -> {
        List<String> analysisImpactExecutionIds = activity.getAnalysisImpactExecutionIds();
        analysisImpactExecutionIds.add(stepExecutionDetail.getUuid());
        activity.setAnalysisImpactExecutionIds(analysisImpactExecutionIds);
        hPersistence.save(activity);
      });
    }
  }

  @Override
  public List<DeploymentActivity> getAnalysisStepAssociatedDeploymentActivities(
      String accountId, String orgIdentifier, String projectIdentifier, String planExecutionId, String stageId) {
    return hPersistence.createQuery(DeploymentActivity.class)
        .filter(ActivityKeys.accountId, accountId)
        .filter(ActivityKeys.orgIdentifier, orgIdentifier)
        .filter(ActivityKeys.projectIdentifier, projectIdentifier)
        .filter(DeploymentActivityKeys.planExecutionId, planExecutionId)
        .filter(DeploymentActivityKeys.stageId, stageId)
        .filter(ActivityKeys.type, ActivityType.DEPLOYMENT)
        .asList();
  }

  private void mapSRMAnalysisExecutionsToDeploymentActivities(String activityId) {
    DeploymentActivity deploymentActivity = hPersistence.get(DeploymentActivity.class, activityId);
    if (deploymentActivity != null) {
      List<SRMAnalysisStepDetailDTO> analysisStepDetails =
          srmAnalysisStepService.getSRMAnalysisSummaries(deploymentActivity.getAccountId(),
              deploymentActivity.getOrgIdentifier(), deploymentActivity.getProjectIdentifier(),
              deploymentActivity.getPlanExecutionId(), deploymentActivity.getStageId());
      List<String> analysisImpactExecutionIds = deploymentActivity.getAnalysisImpactExecutionIds();
      analysisImpactExecutionIds.addAll(analysisStepDetails.stream()
                                            .map(SRMAnalysisStepDetailDTO::getExecutionDetailIdentifier)
                                            .collect(Collectors.toList()));
      deploymentActivity.setAnalysisImpactExecutionIds(analysisImpactExecutionIds);
      hPersistence.save(deploymentActivity);
    }
  }

  private ChangeSummaryDTO getChangeSummary(ProjectParams projectParams, List<String> monitoredServiceIdentifiers,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime, boolean isMonitoredServiceIdentifierScoped) {
    Map<ChangeCategory, Map<Integer, Integer>> changeCategoryToIndexToCount =
        Arrays.stream(ChangeCategory.values())
            .collect(Collectors.toMap(Function.identity(), c -> new HashMap<>(), (u, v) -> u, LinkedHashMap::new));
    startTime = roundDownTo5MinBoundary(startTime);
    endTime = roundUpTo5MinBoundary(endTime);
    getTimelineObject(projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes,
        startTime.minus(Duration.between(startTime, endTime)), endTime, 2, isMonitoredServiceIdentifierScoped)
        .forEachRemaining(timelineObject -> {
          ChangeCategory changeCategory = ChangeSourceType.ofActivityType(timelineObject.id.type).getChangeCategory();
          Map<Integer, Integer> indexToCountMap = changeCategoryToIndexToCount.get(changeCategory);
          Integer index = timelineObject.id.index;
          Integer countSoFar = indexToCountMap.getOrDefault(index, 0);
          countSoFar = countSoFar + timelineObject.count;
          changeCategoryToIndexToCount.get(changeCategory).put(timelineObject.id.index, countSoFar);
        });

    long currentTotalCount =
        changeCategoryToIndexToCount.values().stream().map(c -> c.getOrDefault(1, 0)).mapToLong(num -> num).sum();
    long previousTotalCount =
        changeCategoryToIndexToCount.values().stream().map(c -> c.getOrDefault(0, 0)).mapToLong(num -> num).sum();
    CategoryCountDetails total =
        CategoryCountDetails.builder()
            .count(currentTotalCount)
            .countInPrecedingWindow(previousTotalCount)
            .percentageChange(MathUtils.getPercentageChange(currentTotalCount, previousTotalCount))
            .build();

    return ChangeSummaryDTO.builder()
        .total(total)
        .categoryCountMap(changeCategoryToIndexToCount.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            entry
            -> CategoryCountDetails.builder()
                   .count(entry.getValue().getOrDefault(1, 0))
                   .countInPrecedingWindow(entry.getValue().getOrDefault(0, 0))
                   .percentageChange(MathUtils.getPercentageChange(
                       entry.getValue().getOrDefault(1, 0), entry.getValue().getOrDefault(0, 0)))
                   .build(),
            (u, v) -> u, LinkedHashMap::new)))
        .build();
  }

  private List<Criteria> getCriterias(Query<Activity> q, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime) {
    Stream<ChangeSourceType> changeSourceTypeStream = getChangeSourceEvent(changeCategories, changeSourceTypes);
    return new ArrayList<>(Arrays.asList(
        q.criteria(ActivityKeys.type)
            .in(changeSourceTypeStream.map(ChangeSourceType::getActivityType).collect(Collectors.toList())),
        q.criteria(ActivityKeys.eventTime).lessThan(endTime),
        q.criteria(ActivityKeys.eventTime).greaterThanOrEq(startTime)));
  }

  private Stream<ChangeSourceType> getChangeSourceEvent(
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes) {
    Stream<ChangeSourceType> changeSourceTypeStream = Arrays.stream(ChangeSourceType.values());
    if (CollectionUtils.isNotEmpty(changeCategories)) {
      changeSourceTypeStream = changeSourceTypeStream.filter(
          changeSourceType -> changeCategories.contains(changeSourceType.getChangeCategory()));
    }
    if (CollectionUtils.isNotEmpty(changeSourceTypes)) {
      changeSourceTypeStream = changeSourceTypeStream.filter(changeSourceTypes::contains);
    }
    return changeSourceTypeStream;
  }

  private Query<Activity> createQuery(Instant startTime, Instant endTime, ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, boolean isMonitoredServiceIdentifierScoped) {
    Query<Activity> query;

    query = hPersistence.createQuery(Activity.class, EnumSet.of(COUNT));

    List<Criteria> criteria = getCriterias(query, changeCategories, changeSourceTypes, startTime, endTime);
    Criteria[] criteriasForAppAndInfraEvents = getCriteriasForAppAndInfraEvents(
        query, projectParams, monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped);
    query.and(criteria.toArray(new Criteria[criteria.size()]));
    query.and(criteriasForAppAndInfraEvents);
    query.useReadPreference(ReadPreference.secondaryPreferred());
    return query;
  }

  private Criteria[] getCriteriasForAppAndInfraEvents(Query<Activity> query, ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, boolean isMonitoredServiceIdentifierScoped) {
    if (isMonitoredServiceIdentifierScoped) {
      List<ResourceParams> monitoredServiceIdentifiersWithParams =
          ScopedInformation.getResourceParamsFromScopedIdentifiers(monitoredServiceIdentifiers);
      Criteria[] criteriasForAppEvents = new Criteria[monitoredServiceIdentifiersWithParams.size()];
      for (int i = 0; i < monitoredServiceIdentifiersWithParams.size(); i++) {
        criteriasForAppEvents[i] =
            query.and(query.criteria(ActivityKeys.accountId)
                          .equal(monitoredServiceIdentifiersWithParams.get(i).getAccountIdentifier()),
                query.criteria(ActivityKeys.orgIdentifier)
                    .equal(monitoredServiceIdentifiersWithParams.get(i).getOrgIdentifier()),
                query.criteria(ActivityKeys.projectIdentifier)
                    .equal(monitoredServiceIdentifiersWithParams.get(i).getProjectIdentifier()),
                query.criteria(ActivityKeys.monitoredServiceIdentifier)
                    .equal(monitoredServiceIdentifiersWithParams.get(i).getIdentifier()));
      }
      Criteria[] criteriasForInfraEvents = new Criteria[monitoredServiceIdentifiersWithParams.size()];
      for (int i = 0; i < monitoredServiceIdentifiersWithParams.size(); i++) {
        criteriasForInfraEvents[i] =
            query.and(query.criteria(ActivityKeys.accountId)
                          .equal(monitoredServiceIdentifiersWithParams.get(i).getAccountIdentifier()),
                query.criteria(ActivityKeys.orgIdentifier)
                    .equal(monitoredServiceIdentifiersWithParams.get(i).getOrgIdentifier()),
                query.criteria(ActivityKeys.projectIdentifier)
                    .equal(monitoredServiceIdentifiersWithParams.get(i).getProjectIdentifier()),
                query
                    .criteria(KubernetesClusterActivityKeys.relatedAppServices + "."
                        + ServiceEnvironmentKeys.monitoredServiceIdentifier)
                    .equal(monitoredServiceIdentifiersWithParams.get(i).getIdentifier()));
      }
      return new Criteria[] {query.or(query.or(criteriasForInfraEvents), query.or(criteriasForAppEvents))};
    } else {
      return new Criteria[] {
          query.and(query.criteria(ActivityKeys.accountId).equal(projectParams.getAccountIdentifier()),
              query.criteria(ActivityKeys.orgIdentifier).equal(projectParams.getOrgIdentifier()),
              query.criteria(ActivityKeys.projectIdentifier).equal(projectParams.getProjectIdentifier()),
              query.or(query.criteria(ActivityKeys.monitoredServiceIdentifier).in(monitoredServiceIdentifiers),
                  query
                      .criteria(KubernetesClusterActivityKeys.relatedAppServices + "."
                          + ServiceEnvironmentKeys.monitoredServiceIdentifier)
                      .in(monitoredServiceIdentifiers)))};
    }
  }

  @VisibleForTesting
  Query<ActivityBucket> createQueryForActivityBucket(Instant startTime, Instant endTime, ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, boolean isMonitoredServiceIdentifierScoped) {
    Query<ActivityBucket> query = hPersistence.createQuery(ActivityBucket.class);
    Stream<ChangeSourceType> changeSourceTypeStream = getChangeSourceEvent(changeCategories, changeSourceTypes);
    List<Criteria> criteria = new ArrayList<>(Arrays.asList(
        query.criteria(ActivityBucketKeys.type)
            .in(changeSourceTypeStream.map(ChangeSourceType::getActivityType).collect(Collectors.toList())),
        query.criteria(ActivityBucketKeys.bucketTime).lessThan(endTime),
        query.criteria(ActivityBucketKeys.bucketTime).greaterThanOrEq(startTime)));
    Criteria[] criteriaForAppAndInfraEvents = getCriteriaForAppAndInfraEventsFromActivityBucket(
        query, projectParams, monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped);
    query.and(criteria.toArray(new Criteria[0]));
    query.and(criteriaForAppAndInfraEvents);
    query.useReadPreference(ReadPreference.secondaryPreferred());
    return query;
  }
  private Criteria[] getCriteriaForAppAndInfraEventsFromActivityBucket(Query<?> query, ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, boolean isMonitoredServiceIdentifierScoped) {
    if (isMonitoredServiceIdentifierScoped) {
      List<ResourceParams> monitoredServiceIdentifiersWithParams =
          ScopedInformation.getResourceParamsFromScopedIdentifiers(monitoredServiceIdentifiers);
      Criteria[] criteria = new Criteria[monitoredServiceIdentifiersWithParams.size()];
      for (int i = 0; i < monitoredServiceIdentifiersWithParams.size(); i++) {
        criteria[i] = query.and(query.criteria(ActivityBucketKeys.accountId)
                                    .equal(monitoredServiceIdentifiersWithParams.get(i).getAccountIdentifier()),
            query.criteria(ActivityBucketKeys.orgIdentifier)
                .equal(monitoredServiceIdentifiersWithParams.get(i).getOrgIdentifier()),
            query.criteria(ActivityBucketKeys.projectIdentifier)
                .equal(monitoredServiceIdentifiersWithParams.get(i).getProjectIdentifier()),
            query.criteria(ActivityBucketKeys.monitoredServiceIdentifiers)
                .equal(monitoredServiceIdentifiersWithParams.get(i).getIdentifier()));
      }
      return new Criteria[] {query.or(criteria)};
    } else {
      return new Criteria[] {
          query.and(query.criteria(ActivityBucketKeys.accountId).equal(projectParams.getAccountIdentifier()),
              query.criteria(ActivityBucketKeys.orgIdentifier).equal(projectParams.getOrgIdentifier()),
              query.criteria(ActivityBucketKeys.projectIdentifier).equal(projectParams.getProjectIdentifier()),
              query.criteria(ActivityBucketKeys.monitoredServiceIdentifiers).in(monitoredServiceIdentifiers))};
    }
  }

  @VisibleForTesting
  static class TimelineObject {
    @Id TimelineKey id;
    Integer count;
  }

  @VisibleForTesting
  static class TimelineKey {
    ActivityType type;
    Integer index;
  }
}
