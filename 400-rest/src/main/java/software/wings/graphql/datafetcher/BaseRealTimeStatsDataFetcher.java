/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Projection.projection;
import static dev.morphia.query.Sort.ascending;
import static dev.morphia.query.Sort.descending;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.instance.dashboard.EntitySummary;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractStatsDataFetcher.TwoLevelAggregatedData;
import software.wings.graphql.schema.type.aggregation.QLAggregatedData;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLDataPoint;
import software.wings.graphql.schema.type.aggregation.QLReference;
import software.wings.graphql.schema.type.aggregation.QLSinglePointData;
import software.wings.graphql.schema.type.aggregation.QLStackedData;
import software.wings.graphql.schema.type.aggregation.QLStackedDataPoint;
import software.wings.graphql.utils.nameservice.NameResult;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.service.impl.instance.FlatEntitySummaryStats;

import com.google.common.collect.Lists;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.Accumulator;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.aggregation.Group;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

@OwnedBy(DX)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public interface BaseRealTimeStatsDataFetcher<F> extends BaseStatsDataFetcher {
  default QLData getStackedData(NameService nameService, WingsPersistence wingsPersistence, List<String> groupBy,
      Class entityClass, Query query) {
    String firstLevelAggregation = groupBy.get(0);
    String secondLevelAggregation = groupBy.get(1);
    String entityIdColumn = getAggregationFieldName(firstLevelAggregation);
    String secondLevelEntityIdColumn = getAggregationFieldName(secondLevelAggregation);
    int limit = wingsPersistence.getMaxDocumentLimit(entityClass);

    List<TwoLevelAggregatedData> aggregatedDataList = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(query.getEntityClass())
            .createAggregation(entityClass)
            .match(query)
            .group(Group.id(getFirstLevelGrouping(entityIdColumn), getFirstLevelGrouping(secondLevelEntityIdColumn)),
                grouping("count", new Accumulator("$sum", 1)),
                grouping("firstLevelInfo",
                    grouping("$first", projection("id", entityIdColumn), projection("name", entityIdColumn))),
                grouping("secondLevelInfo",
                    grouping("$first", projection("id", secondLevelEntityIdColumn),
                        projection("name", secondLevelEntityIdColumn))))
            .sort(
                ascending("_id." + entityIdColumn), ascending("_id." + secondLevelEntityIdColumn), descending("count"));
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(TwoLevelAggregatedData.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(entityClass), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(aggregatedDataList::add);

    return getStackedData(nameService, aggregatedDataList, firstLevelAggregation, secondLevelAggregation);
  }

  default Group getFirstLevelGrouping(String entityIdColumn) {
    if (entityIdColumn.contains(".")) {
      String[] split = entityIdColumn.split("\\.");
      if (split.length != 2) {
        return grouping(entityIdColumn);
      }
      return grouping(split[0], new Accumulator(split[1], entityIdColumn));
    } else {
      return grouping(entityIdColumn);
    }
  }

  default QLAggregatedData getAggregatedData(WingsPersistence wingsPersistence, NameService nameService,
      List<String> groupBy, Class entityClass, Query query) {
    String firstLevelAggregation = groupBy.get(0);
    String entityIdColumn = getAggregationFieldName(firstLevelAggregation);
    List<QLDataPoint> dataPoints = new ArrayList<>();
    List<FlatEntitySummaryStats> summaryStats = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(entityClass)
            .createAggregation(entityClass)
            .match(query)
            .group(Group.id(getFirstLevelGrouping(entityIdColumn)), grouping("count", new Accumulator("$sum", 1)))
            .project(projection("_id").suppress(), projection("entityId", "_id." + entityIdColumn),
                projection("entityName", "_id." + entityIdColumn), projection("count"));
    int limit = wingsPersistence.getMaxDocumentLimit(entityClass);
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(FlatEntitySummaryStats.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(entityClass), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(summaryStats::add);

    return getQlAggregatedData(nameService, firstLevelAggregation, dataPoints, summaryStats);
  }

  default QLAggregatedData getQlAggregatedData(NameService nameService, String firstLevelAggregation,
      List<QLDataPoint> dataPoints, List<FlatEntitySummaryStats> summaryStats) {
    Set<String> ids = summaryStats.stream().map(FlatEntitySummaryStats::getEntityId).collect(Collectors.toSet());
    NameResult nameResult = nameService.getNames(ids, firstLevelAggregation);
    summaryStats.forEach(getFlatEntitySummaryStatsConsumer(firstLevelAggregation, dataPoints, nameResult));
    return QLAggregatedData.builder().dataPoints(dataPoints).build();
  }

  @NotNull
  default Consumer<FlatEntitySummaryStats> getFlatEntitySummaryStatsConsumer(
      String firstLevelAggregation, List<QLDataPoint> dataPoints, NameResult nameResult) {
    return flatEntitySummaryStats -> {
      QLDataPoint dataPoint = getDataPoint(flatEntitySummaryStats, firstLevelAggregation, nameResult);
      dataPoints.add(dataPoint);
    };
  }

  default QLData getSingleDataPointData(Query query) {
    long count = query.count();
    return QLSinglePointData.builder()
        .dataPoint(QLDataPoint.builder()
                       .key(QLReference.builder().name(getEntityType()).id(getEntityType()).build())
                       .value(count)
                       .build())
        .build();
  }

  default QLData getQLData(DataFetcherUtils utils, NameService nameService, WingsPersistence wingsPersistence,
      String accountId, List<F> filters, Class entityClass, List<String> groupByAsStringList) {
    Query query = populateFilters(utils, wingsPersistence, accountId, filters, entityClass);
    if (isNotEmpty(groupByAsStringList)) {
      if (groupByAsStringList.size() == 1) {
        return getAggregatedData(wingsPersistence, nameService, groupByAsStringList, entityClass, query);
      } else if (groupByAsStringList.size() == 2) {
        return getStackedData(nameService, wingsPersistence, groupByAsStringList, entityClass, query);
      } else {
        throw new WingsException("Only 2 level aggregations supported right now");
      }
    } else {
      return getSingleDataPointData(query);
    }
  }

  void populateFilters(String accountId, List<F> filters, Query query);

  @NotNull
  default Query populateFilters(
      DataFetcherUtils utils, WingsPersistence wingsPersistence, String accountId, List<F> filters, Class entityClass) {
    Query query = utils.populateAccountFilter(wingsPersistence, accountId, entityClass);
    populateFilters(accountId, filters, query);
    return query;
  }

  String getAggregationFieldName(String aggregation);

  default QLStackedData getStackedData(NameService nameService, List<TwoLevelAggregatedData> aggregatedDataList,
      String firstLevelType, String secondLevelType) {
    QLStackedDataPoint prevStackedDataPoint = null;
    List<QLStackedDataPoint> stackedDataPointList = new ArrayList<>();

    Set<String> firstLevelIds = aggregatedDataList.stream()
                                    .filter(aggregationData -> aggregationData.getFirstLevelInfo().getId() != null)
                                    .map(aggregationData -> aggregationData.getFirstLevelInfo().getId())
                                    .collect(Collectors.toSet());

    Set<String> secondLevelIds = aggregatedDataList.stream()
                                     .filter(aggregationData -> aggregationData.getSecondLevelInfo().getId() != null)
                                     .map(aggregationData -> aggregationData.getSecondLevelInfo().getId())
                                     .collect(Collectors.toSet());

    NameResult firstLevelNameResult = nameService.getNames(firstLevelIds, firstLevelType);
    NameResult secondLevelNameResult = nameService.getNames(secondLevelIds, secondLevelType);

    for (TwoLevelAggregatedData aggregatedData : aggregatedDataList) {
      EntitySummary firstLevelInfo = aggregatedData.getFirstLevelInfo();
      EntitySummary secondLevelInfo = aggregatedData.getSecondLevelInfo();
      QLReference secondLevelRef =
          QLReference.builder()
              .type(secondLevelType)
              .name(getEntityName(secondLevelNameResult, secondLevelInfo.getId(), secondLevelType))
              .id(secondLevelInfo.getId())
              .build();
      QLDataPoint secondLevelDataPoint =
          QLDataPoint.builder().key(secondLevelRef).value(aggregatedData.getCount()).build();

      boolean sameAsPrevious = prevStackedDataPoint != null && prevStackedDataPoint.getKey().getId() != null
          && prevStackedDataPoint.getKey().getId().equals(firstLevelInfo.getId());
      if (sameAsPrevious) {
        prevStackedDataPoint.getValues().add(secondLevelDataPoint);
      } else {
        QLReference firstLevelRef =
            QLReference.builder()
                .type(firstLevelType)
                .name(getEntityName(firstLevelNameResult, firstLevelInfo.getId(), firstLevelType))
                .id(firstLevelInfo.getId())
                .build();
        prevStackedDataPoint =
            QLStackedDataPoint.builder().key(firstLevelRef).values(Lists.newArrayList(secondLevelDataPoint)).build();
        stackedDataPointList.add(prevStackedDataPoint);
      }
    }
    return QLStackedData.builder().dataPoints(stackedDataPointList).build();
  }

  default QLDataPoint getDataPoint(FlatEntitySummaryStats stats, String entityType, NameResult nameResult) {
    QLReference qlReference = QLReference.builder()
                                  .type(entityType)
                                  .name(getEntityName(nameResult, stats.getEntityId(), entityType))
                                  .id(stats.getEntityId())
                                  .build();
    return QLDataPoint.builder().value(stats.getCount()).key(qlReference).build();
  }

  default String getEntityName(NameResult nameResult, String id, String type) {
    if (nameResult.getIdNameMap().containsKey(id)) {
      return nameResult.getIdNameMap().get(id);
    }
    return NameResult.DELETED;
  }
}
