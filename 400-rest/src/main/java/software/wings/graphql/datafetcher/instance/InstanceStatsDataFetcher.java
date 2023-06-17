/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Projection.projection;
import static dev.morphia.query.Sort.ascending;
import static dev.morphia.query.Sort.descending;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.graphql.datafetcher.RealTimeStatsDataFetcherWithTags;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLAggregatedData;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLDataPoint;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.QLSinglePointData;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceEntityAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.service.impl.instance.FlatEntitySummaryStats;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.aggregation.Group;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InstanceStatsDataFetcher
    extends RealTimeStatsDataFetcherWithTags<QLNoOpAggregateFunction, QLInstanceFilter, QLInstanceAggregation,
        QLNoOpSortCriteria, QLInstanceTagType, QLInstanceTagAggregation, QLInstanceEntityAggregation> {
  @Inject private InstanceTimeSeriesDataHelper timeSeriesDataHelper;
  @Inject private InstanceQueryHelper instanceMongoHelper;
  @Inject private TagHelper tagHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject LongerDataRetentionService longerDataRetentionService;

  @Override
  protected QLInstanceTagAggregation getTagAggregation(QLInstanceAggregation groupBy) {
    return groupBy.getTagAggregation();
  }

  @Override
  protected EntityType getEntityType(QLInstanceTagType entityType) {
    return instanceMongoHelper.getEntityType(entityType);
  }

  @Override
  protected QLData fetch(String accountId, QLNoOpAggregateFunction aggregateFunction, List<QLInstanceFilter> filters,
      List<QLInstanceAggregation> groupByList, List<QLNoOpSortCriteria> sortCriteria,
      DataFetchingEnvironment dataFetchingEnvironment) {
    validateAggregations(groupByList);

    QLTimeSeriesAggregation groupByTime = getGroupByTime(groupByList);
    List<QLInstanceEntityAggregation> groupByEntityList = getGroupByEntity(groupByList);
    List<QLInstanceTagAggregation> groupByTagList = getGroupByTag(groupByList);

    groupByEntityList = getGroupByEntityListFromTags(groupByList, groupByEntityList, groupByTagList, groupByTime);

    if (groupByTime != null) {
      if (isNotEmpty(filters)) {
        filters.forEach(filter -> {
          // TODO No use of newFilters here, check and delete it if not required
          List<QLInstanceFilter> newFilters = new ArrayList<>();
          if (filter.getTag() != null) {
            // Process all tag filters and convert them to main entity filters
            QLInstanceTagFilter tagFilter = filter.getTag();
            List<QLTagInput> tags = tagFilter.getTags();
            Set<String> entityIds =
                tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(tagFilter.getEntityType()));
            switch (tagFilter.getEntityType()) {
              case APPLICATION:
                filter.setApplication(getMergedIdFilter(entityIds, filter.getApplication()));
                break;
              case SERVICE:
                filter.setService(getMergedIdFilter(entityIds, filter.getService()));
                break;
              case ENVIRONMENT:
                filter.setEnvironment(getMergedIdFilter(entityIds, filter.getEnvironment()));
                break;
              default:
                log.error("EntityType {} not supported in query", tagFilter.getEntityType());
                throw new InvalidRequestException("Error while compiling query", WingsException.USER);
            }
          } else {
            newFilters.add(filter);
          }
        });
      }

      if (isNotEmpty(groupByEntityList)) {
        if (featureFlagService.isEnabled(
                FeatureName.CUSTOM_DASHBOARD_INSTANCE_FETCH_LONGER_RETENTION_DATA, accountId)) {
          return timeSeriesDataHelper.getTimeSeriesAggregatedDataUsingNewAggregators(
              accountId, aggregateFunction, filters, groupByTime, groupByEntityList.get(0));
        } else {
          return timeSeriesDataHelper.getTimeSeriesAggregatedData(
              accountId, aggregateFunction, filters, groupByTime, groupByEntityList.get(0));
        }
      } else {
        return timeSeriesDataHelper.getTimeSeriesData(accountId, aggregateFunction, filters, groupByTime);
      }
    } else {
      if (isNotEmpty(filters)) {
        Optional<QLInstanceFilter> timeFilter =
            filters.stream().filter(filter -> filter.getCreatedAt() != null).findFirst();
        if (timeFilter.isPresent()) {
          throw new InvalidRequestException(
              "Time Filter is only supported for time series data (grouped by time)", WingsException.USER);
        }
      }

      Query<Instance> query = wingsPersistence.createAnalyticsQuery(Instance.class);
      query.filter("accountId", accountId);
      query.filter("isDeleted", false);

      instanceMongoHelper.setQuery(accountId, filters, query);
      int limit = wingsPersistence.getMaxDocumentLimit(Instance.class);

      if (isNotEmpty(groupByList)) {
        if (groupByList.size() == 1) {
          QLInstanceEntityAggregation firstLevelAggregation = groupByEntityList.get(0);
          String entityIdColumn = getMongoFieldName(firstLevelAggregation);
          String entityNameColumn = getNameField(firstLevelAggregation);
          List<QLDataPoint> dataPoints = new ArrayList<>();

          AggregationPipeline aggregationPipeline =
              wingsPersistence.getDefaultAnalyticsDatastore(Instance.class)
                  .createAggregation(Instance.class)
                  .match(query)
                  .group(Group.id(grouping(entityIdColumn)), grouping("count", accumulator("$sum", 1)),
                      grouping(entityNameColumn, grouping("$first", entityNameColumn)))
                  .project(projection("_id").suppress(), projection("entityId", "_id." + entityIdColumn),
                      projection("entityName", entityNameColumn), projection("count"));
          if (limit > 0) {
            aggregationPipeline.limit(limit);
          }
          aggregationPipeline
              .aggregate(FlatEntitySummaryStats.class,
                  AggregationOptions.builder()
                      .maxTime(wingsPersistence.getMaxTimeMs(Instance.class), TimeUnit.MILLISECONDS)
                      .build())
              .forEachRemaining(flatEntitySummaryStats -> {
                QLDataPoint dataPoint = getDataPoint(flatEntitySummaryStats, firstLevelAggregation.name());
                dataPoints.add(dataPoint);
              });
          return QLAggregatedData.builder().dataPoints(dataPoints).build();
        } else if (groupByList.size() == 2) {
          QLInstanceEntityAggregation firstLevelAggregation = groupByEntityList.get(0);
          QLInstanceEntityAggregation secondLevelAggregation = groupByEntityList.get(1);
          String entityIdColumn = getMongoFieldName(firstLevelAggregation);
          String entityNameColumn = getNameField(firstLevelAggregation);
          String secondLevelEntityIdColumn = getMongoFieldName(secondLevelAggregation);
          String secondLevelEntityNameColumn = getNameField(secondLevelAggregation);

          List<TwoLevelAggregatedData> aggregatedDataList = new ArrayList<>();
          AggregationPipeline aggregationPipeline =
              wingsPersistence.getDefaultAnalyticsDatastore(query.getEntityClass())
                  .createAggregation(Instance.class)
                  .match(query)
                  .group(Group.id(grouping(entityIdColumn), grouping(secondLevelEntityIdColumn)),
                      grouping("count", accumulator("$sum", 1)),
                      grouping("firstLevelInfo",
                          grouping("$first", projection("id", entityIdColumn), projection("name", entityNameColumn))),
                      grouping("secondLevelInfo",
                          grouping("$first", projection("id", secondLevelEntityIdColumn),
                              projection("name", secondLevelEntityNameColumn))))
                  .sort(ascending("_id." + entityIdColumn), ascending("_id." + secondLevelEntityIdColumn),
                      descending("count"));
          if (limit > 0) {
            aggregationPipeline.limit(limit);
          }
          aggregationPipeline
              .aggregate(TwoLevelAggregatedData.class,
                  AggregationOptions.builder()
                      .maxTime(wingsPersistence.getMaxTimeMs(Instance.class), TimeUnit.MILLISECONDS)
                      .build())
              .forEachRemaining(aggregatedDataList::add);

          return getStackedData(
              nameService, aggregatedDataList, firstLevelAggregation.name(), secondLevelAggregation.name());

        } else {
          log.warn("Only one or two level aggregations supported right now");
          throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
        }
      } else {
        long count = query.count();
        return QLSinglePointData.builder().dataPoint(QLDataPoint.builder().value(count).build()).build();
      }
    }
  }

  private QLIdFilter getMergedIdFilter(Set<String> entityIds, QLIdFilter idFilter) {
    if (isEmpty(entityIds)) {
      return idFilter;
    }

    if (idFilter != null) {
      String[] values = idFilter.getValues();
      Set<String> valueSet = isNotEmpty(values) ? Sets.newHashSet(values) : Sets.newHashSet();
      valueSet.addAll(entityIds);
      return QLIdFilter.builder().operator(QLIdOperator.IN).values(valueSet.toArray(new String[0])).build();
    } else {
      return QLIdFilter.builder().operator(QLIdOperator.IN).values(entityIds.toArray(new String[0])).build();
    }
  }

  private QLTimeSeriesAggregation getGroupByTime(List<QLInstanceAggregation> groupBy) {
    if (groupBy != null) {
      Optional<QLTimeSeriesAggregation> first = groupBy.stream()
                                                    .filter(g -> g.getTimeAggregation() != null)
                                                    .map(QLInstanceAggregation::getTimeAggregation)
                                                    .findFirst();
      if (first.isPresent()) {
        return first.get();
      }
    }
    return null;
  }

  private List<QLInstanceEntityAggregation> getGroupByEntity(List<QLInstanceAggregation> groupBy) {
    return groupBy != null ? groupBy.stream()
                                 .filter(g -> g.getEntityAggregation() != null)
                                 .map(QLInstanceAggregation::getEntityAggregation)
                                 .collect(Collectors.toList())
                           : null;
  }

  private void validateAggregations(List<QLInstanceAggregation> groupByList) {
    // TODO
  }

  @Override
  public void populateFilters(String accountId, List<QLInstanceFilter> filters, Query query) {
    instanceMongoHelper.setQuery(accountId, filters, query);
  }

  private String getMongoFieldName(QLInstanceEntityAggregation aggregation) {
    switch (aggregation) {
      case Application:
        return "appId";
      case Service:
        return "serviceId";
      case Environment:
        return "envId";
      case CloudProvider:
        return "computeProviderId";
      case InstanceType:
        return "instanceType";
      default:
        log.warn("Unknown aggregation type" + aggregation);
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }

  private String getNameField(QLInstanceEntityAggregation aggregation) {
    switch (aggregation) {
      case Application:
        return "appName";
      case Service:
        return "serviceName";
      case Environment:
        return "envName";
      case CloudProvider:
        return "computeProviderName";
      case InstanceType:
        return "instanceType";
      default:
        log.warn("Unknown aggregation type" + aggregation);
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }

  @Override
  public String getAggregationFieldName(String aggregation) {
    return null;
  }

  @Override
  public String getEntityType() {
    return NameService.instance;
  }

  @Override
  protected QLInstanceEntityAggregation getEntityAggregation(QLInstanceAggregation groupBy) {
    return groupBy.getEntityAggregation();
  }

  @Override
  protected QLInstanceEntityAggregation getGroupByEntityFromTag(QLInstanceTagAggregation groupByTag) {
    switch (groupByTag.getEntityType()) {
      case APPLICATION:
        return QLInstanceEntityAggregation.Application;
      case SERVICE:
        return QLInstanceEntityAggregation.Service;
      case ENVIRONMENT:
        return QLInstanceEntityAggregation.Environment;
      default:
        log.warn("Unsupported tag entity type {}", groupByTag.getEntityType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }
}
