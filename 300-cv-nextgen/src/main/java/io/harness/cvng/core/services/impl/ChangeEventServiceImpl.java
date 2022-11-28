/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.QueryChecks.COUNT;

import static org.mongodb.morphia.aggregation.Accumulator.accumulator;
import static org.mongodb.morphia.aggregation.Group.grouping;
import static org.mongodb.morphia.aggregation.Group.id;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.KubernetesClusterActivityKeys;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.RelatedAppMonitoredService.ServiceEnvironmentKeys;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO.CategoryCountDetails;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.change.ChangeTimeline.ChangeTimelineBuilder;
import io.harness.cvng.core.beans.change.ChangeTimeline.TimeRangeDetail;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.transformer.changeEvent.ChangeEventEntityAndDTOTransformer;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery.QueryChecks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.AggregationOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

// TODO: merge ChangeEventService and ActivityService
public class ChangeEventServiceImpl implements ChangeEventService {
  @Inject ChangeSourceService changeSourceService;
  @Inject ChangeEventEntityAndDTOTransformer transformer;
  @Inject ActivityService activityService;
  @Inject HPersistence hPersistence;
  @Inject MonitoredServiceService monitoredServiceService;

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
    Optional<ChangeSource> changeSourceOptional =
        changeSourceService.getEntityByType(monitoredServiceParams, changeEventDTO.getType())
            .stream()
            .filter(source -> source.isEnabled())
            .findAny();
    if (!changeSourceOptional.isPresent()) {
      return false;
    }
    if (StringUtils.isEmpty(changeEventDTO.getChangeSourceIdentifier())) {
      changeEventDTO.setChangeSourceIdentifier(changeSourceOptional.get().getIdentifier());
    }
    activityService.upsert(transformer.getEntity(changeEventDTO));
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
        Arrays.asList(monitoredServiceParams.getMonitoredServiceIdentifier()), null, null, startTime, endTime);
  }

  @Override
  public PageResponse<ChangeEventDTO> getChangeEvents(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifier, List<String> monitoredServiceIdentifiers, String searchText,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime, PageRequest pageRequest) {
    if (isNotEmpty(monitoredServiceIdentifiers)) {
      Preconditions.checkState(isEmpty(serviceIdentifiers) && isEmpty(environmentIdentifier),
          "serviceIdentifier, envIdentifier filter can not be used with monitoredServiceIdentifier filter");
      return getChangeEvents(projectParams, monitoredServiceIdentifiers, searchText, changeCategories,
          changeSourceTypes, startTime, endTime, pageRequest);
    } else {
      return getChangeEvents(projectParams, serviceIdentifiers, environmentIdentifier, searchText, changeCategories,
          changeSourceTypes, startTime, endTime, pageRequest);
    }
  }

  @Override
  public PageResponse<ChangeEventDTO> getChangeEvents(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifier, String searchText, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, PageRequest pageRequest) {
    List<String> monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
        projectParams, serviceIdentifiers, environmentIdentifier);
    return getChangeEvents(projectParams, monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes,
        startTime, endTime, pageRequest);
  }
  private PageResponse<ChangeEventDTO> getChangeEvents(ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, String searchText, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, PageRequest pageRequest) {
    List<Activity> activities = createQuery(
        projectParams, startTime, endTime, monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes)
                                    .order(Sort.descending(ActivityKeys.eventTime))
                                    .asList(new FindOptions()
                                                .skip(pageRequest.getPageIndex() * pageRequest.getPageSize())
                                                .limit(pageRequest.getPageSize()));
    Long total = createQuery(
        projectParams, startTime, endTime, monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes)
                     .count();
    Long totalPages = (total / pageRequest.getPageSize()) + ((total % pageRequest.getPageSize()) == 0 ? 0 : 1);
    return PageResponse.<ChangeEventDTO>builder()
        .pageIndex(pageRequest.getPageIndex())
        .totalPages(totalPages)
        .pageSize(pageRequest.getPageSize())
        .totalItems(total)
        .pageItemCount(activities.size())
        .content(activities.stream().map(transformer::getDto).collect(Collectors.toList()))
        .build();
  }

  private ChangeTimeline getTimeline(ProjectParams projectParams, List<String> monitoredServiceIdentifiers,
      String searchText, List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes,
      Instant startTime, Instant endTime, Integer pointCount) {
    Map<ChangeCategory, Map<Integer, TimeRangeDetail>> categoryMilliSecondFromStartDetailMap = new HashMap<>();
    Duration timeRangeDuration = Duration.between(startTime, endTime).dividedBy(pointCount);

    getTimelineObject(projectParams, monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes,
        startTime, endTime, pointCount)
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
      List<String> environmentIdentifiers, List<String> monitoredServiceIdentifiers, String searchText,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime, Integer pointCount) {
    if (isNotEmpty(monitoredServiceIdentifiers)) {
      Preconditions.checkState(isEmpty(serviceIdentifiers) && isEmpty(environmentIdentifiers),
          "serviceIdentifier, envIdentifier filter can not be used with monitoredServiceIdentifier filter");
    } else {
      monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
          projectParams, serviceIdentifiers, environmentIdentifiers);
    }
    return getTimeline(projectParams, monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes,
        startTime, endTime, pointCount);
  }

  @Override
  public ChangeTimeline getMonitoredServiceChangeTimeline(MonitoredServiceParams monitoredServiceParams,
      String searchText, List<ChangeSourceType> changeSourceTypes, DurationDTO duration, Instant endTime) {
    HeatMapResolution resolution = HeatMapResolution.resolutionForDurationDTO(duration);
    Instant trendEndTime = resolution.getNextResolutionEndTime(endTime);
    Instant trendStartTime = trendEndTime.minus(duration.getDuration());
    String monitoredServiceIdentifier = monitoredServiceParams.getMonitoredServiceIdentifier();
    Preconditions.checkNotNull(monitoredServiceIdentifier, "monitoredServiceIdentifier can not be null");
    return getTimeline(monitoredServiceParams, Arrays.asList(monitoredServiceIdentifier), searchText, null,
        changeSourceTypes, trendStartTime, trendEndTime, CVNextGenConstants.CVNG_TIMELINE_BUCKET_COUNT);
  }
  private Iterator<TimelineObject> getTimelineObject(ProjectParams projectParams,
      List<String> monitoredServiceIdentifiers, String searchText, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, Integer pointCount) {
    Duration timeRangeDuration = Duration.between(startTime, endTime).dividedBy(pointCount);
    return hPersistence.getDatastore(Activity.class)
        .createAggregation(Activity.class)
        .match(createQuery(projectParams, startTime, endTime, monitoredServiceIdentifiers, searchText, changeCategories,
            changeSourceTypes))
        .group(id(grouping("type", "type"),
                   grouping("index",
                       accumulator("$floor",
                           accumulator("$divide",
                               Arrays.asList(accumulator("$subtract",
                                                 Arrays.asList("$eventTime", new Date(startTime.toEpochMilli()))),
                                   timeRangeDuration.toMillis()))))),
            grouping("count", accumulator("$sum", 1)))
        .aggregate(TimelineObject.class,
            AggregationOptions.builder()
                .maxTime(hPersistence.getMaxTimeMs(Activity.class), TimeUnit.MILLISECONDS)
                .build());
  }
  @VisibleForTesting
  Iterator<TimelineObject> getTimelineObject(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifier, String searchText, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime, Integer pointCount) {
    List<String> monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
        projectParams, serviceIdentifiers, environmentIdentifier);
    return getTimelineObject(projectParams, monitoredServiceIdentifiers, searchText, changeCategories,
        changeSourceTypes, startTime, endTime, pointCount);
  }

  @Override
  public ChangeSummaryDTO getChangeSummary(ProjectParams projectParams, List<String> serviceIdentifiers,
      List<String> environmentIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime) {
    List<String> monitoredServiceIdentifiers = monitoredServiceService.getMonitoredServiceIdentifiers(
        projectParams, serviceIdentifiers, environmentIdentifiers);
    return getChangeSummary(
        projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime, endTime);
  }

  @Override
  public ChangeSummaryDTO getChangeSummary(ProjectParams projectParams, String monitoredServiceIdentifier,
      List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, Instant startTime, Instant endTime) {
    if (isEmpty(monitoredServiceIdentifiers)) {
      monitoredServiceIdentifiers = Collections.singletonList(monitoredServiceIdentifier);
    }
    return getChangeSummary(
        projectParams, monitoredServiceIdentifiers, changeCategories, changeSourceTypes, startTime, endTime);
  }

  private ChangeSummaryDTO getChangeSummary(ProjectParams projectParams, List<String> monitoredServiceIdentifiers,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime) {
    Map<ChangeCategory, Map<Integer, Integer>> changeCategoryToIndexToCount =
        Arrays.stream(ChangeCategory.values()).collect(Collectors.toMap(Function.identity(), c -> new HashMap<>()));
    getTimelineObject(projectParams, monitoredServiceIdentifiers, null, changeCategories, changeSourceTypes,
        startTime.minus(Duration.between(startTime, endTime)), endTime, 2)
        .forEachRemaining(timelineObject -> {
          ChangeCategory changeCategory = ChangeSourceType.ofActivityType(timelineObject.id.type).getChangeCategory();
          Map<Integer, Integer> indexToCountMap = changeCategoryToIndexToCount.get(changeCategory);
          Integer index = timelineObject.id.index;
          Integer countSoFar = indexToCountMap.getOrDefault(index, 0);
          countSoFar = countSoFar + timelineObject.count;
          changeCategoryToIndexToCount.get(changeCategory).put(timelineObject.id.index, countSoFar);
        });
    return ChangeSummaryDTO.builder()
        .categoryCountMap(changeCategoryToIndexToCount.entrySet().stream().collect(Collectors.toMap(entry
            -> entry.getKey(),
            entry
            -> CategoryCountDetails.builder()
                   .count(entry.getValue().getOrDefault(1, 0))
                   .countInPrecedingWindow(entry.getValue().getOrDefault(0, 0))
                   .build())))
        .build();
  }

  private List<Criteria> getCriterias(Query<Activity> q, ProjectParams projectParams,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, Instant startTime,
      Instant endTime) {
    Stream<ChangeSourceType> changeSourceTypeStream = Arrays.stream(ChangeSourceType.values());
    if (CollectionUtils.isNotEmpty(changeCategories)) {
      changeSourceTypeStream = changeSourceTypeStream.filter(
          changeSourceType -> changeCategories.contains(changeSourceType.getChangeCategory()));
    }
    if (CollectionUtils.isNotEmpty(changeSourceTypes)) {
      changeSourceTypeStream = changeSourceTypeStream.filter(changeSourceTypes::contains);
    }
    return new ArrayList<>(Arrays.asList(q.criteria(ActivityKeys.accountId).equal(projectParams.getAccountIdentifier()),
        q.criteria(ActivityKeys.orgIdentifier).equal(projectParams.getOrgIdentifier()),
        q.criteria(ActivityKeys.projectIdentifier).equal(projectParams.getProjectIdentifier()),
        q.criteria(ActivityKeys.type)
            .in(changeSourceTypeStream.map(ChangeSourceType::getActivityType).collect(Collectors.toList())),
        q.criteria(ActivityKeys.eventTime).lessThan(endTime),
        q.criteria(ActivityKeys.eventTime).greaterThanOrEq(startTime)));
  }

  private Criteria[] getCriteriasForInfraEvents(Query<Activity> q, ProjectParams projectParams, Instant startTime,
      Instant endTime, List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes) {
    List<Criteria> criterias = getCriterias(q, projectParams, changeCategories, changeSourceTypes, startTime, endTime);
    criterias.add(q.criteria(KubernetesClusterActivityKeys.relatedAppServices + "."
                       + ServiceEnvironmentKeys.monitoredServiceIdentifier)
                      .in(CollectionUtils.emptyIfNull(monitoredServiceIdentifiers)));
    return criterias.toArray(new Criteria[criterias.size()]);
  }

  private Criteria[] getCriteriasForAppEvents(Query<Activity> q, ProjectParams projectParams, Instant startTime,
      Instant endTime, List<String> monitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes) {
    List<Criteria> criterias = getCriterias(q, projectParams, changeCategories, changeSourceTypes, startTime, endTime);
    criterias.add(q.criteria(ActivityKeys.monitoredServiceIdentifier)
                      .in(CollectionUtils.emptyIfNull(monitoredServiceIdentifiers)));
    return criterias.toArray(new Criteria[criterias.size()]);
  }

  private Query<Activity> createQuery(ProjectParams projectParams, Instant startTime, Instant endTime,
      List<String> monitoredServiceIdentifiers, String searchText, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes) {
    if (StringUtils.isNotEmpty(searchText)) {
      // For text search top level or doesnt work as only text search operation allowed in a query
      return createTextSearchQuery(projectParams, startTime, endTime, monitoredServiceIdentifiers, searchText,
          changeCategories, changeSourceTypes);
    }
    // authority and validation fails because of top level OR
    Query<Activity> query = hPersistence.createQuery(Activity.class, EnumSet.<QueryChecks>of(COUNT));
    query.or(query.and(getCriteriasForAppEvents(query, projectParams, startTime, endTime, monitoredServiceIdentifiers,
                 changeCategories, changeSourceTypes)),
        query.and(getCriteriasForInfraEvents(query, projectParams, startTime, endTime, monitoredServiceIdentifiers,
            changeCategories, changeSourceTypes)));
    return query;
  }

  @VisibleForTesting
  Query<Activity> createTextSearchQuery(ProjectParams projectParams, Instant startTime, Instant endTime,
      List<String> monitoredServiceIdentifiers, String searchText, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes) {
    Query<Activity> query = hPersistence.createQuery(Activity.class)
                                .filter(ActivityKeys.accountId, projectParams.getAccountIdentifier())
                                .filter(ActivityKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                .filter(ActivityKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                .search(searchText)
                                .field(ActivityKeys.eventTime)
                                .lessThan(endTime)
                                .field(ActivityKeys.eventTime)
                                .greaterThanOrEq(startTime)
                                .disableValidation();
    Stream<ChangeSourceType> changeSourceTypeStream = Arrays.stream(ChangeSourceType.values());
    if (CollectionUtils.isNotEmpty(changeCategories)) {
      changeSourceTypeStream = changeSourceTypeStream.filter(
          changeSourceType -> changeCategories.contains(changeSourceType.getChangeCategory()));
    }
    if (CollectionUtils.isNotEmpty(changeSourceTypes)) {
      changeSourceTypeStream = changeSourceTypeStream.filter(changeSourceTypes::contains);
    }
    query = query.field(ActivityKeys.type)
                .in(changeSourceTypeStream.map(ChangeSourceType::getActivityType).collect(Collectors.toList()));
    query.or(new Criteria[] {query.criteria(ActivityKeys.monitoredServiceIdentifier)
                                 .in(CollectionUtils.emptyIfNull(monitoredServiceIdentifiers)),
        query
            .criteria(KubernetesClusterActivityKeys.relatedAppServices + "."
                + ServiceEnvironmentKeys.monitoredServiceIdentifier)
            .in(CollectionUtils.emptyIfNull(monitoredServiceIdentifiers))});
    return query;
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
