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

import static software.wings.graphql.datafetcher.AbstractStatsDataFetcher.MAX_RETRY;
import static software.wings.graphql.datafetcher.instance.Constants.COMPLETE_AGGREGATION_INTERVAL;
import static software.wings.graphql.datafetcher.instance.Constants.INSTANCE_STATS_DAY_TABLE_NAME;
import static software.wings.graphql.datafetcher.instance.Constants.INSTANCE_STATS_HOUR_TABLE_NAME;
import static software.wings.graphql.datafetcher.instance.Constants.INSTANCE_STATS_TABLE_NAME;
import static software.wings.graphql.datafetcher.instance.Constants.PARTIAL_AGGREGATION_INTERVAL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.exception.WingsException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.schema.type.aggregation.QLDataPoint;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLReference;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.QLTimeAggregationType;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesDataPoint.QLTimeSeriesDataPointBuilder;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceEntityAggregation;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceFilter;
import software.wings.graphql.utils.nameservice.NameResult;
import software.wings.graphql.utils.nameservice.NameService;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class that contains all the methods required to fetch instance time series data
 * from TimeScaleDB.
 * @author rktummala on 06/27/19
 */
@OwnedBy(DX)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InstanceTimeSeriesDataHelper {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject NameService nameService;
  @Inject InstanceStatsDataFetcher instanceStatsDataFetcher;
  private static int DEFAULT_EXPIRY_IN_DAYS = 210;

  public QLStackedTimeSeriesData getTimeSeriesAggregatedData(String accountId,
      QLNoOpAggregateFunction aggregateFunction, List<QLInstanceFilter> filters, QLTimeSeriesAggregation groupByTime,
      QLInstanceEntityAggregation groupByEntity) {
    //    SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY SUM_VALUE) AS CNT,
    //    time_bucket_gapfill('1 days',REPORTEDAT, '2019-08-03T10:35:10.248Z', '2019-08-10T10:35:10.248Z') AS
    //    GRP_BY_TIME, ENTITY_ID FROM (SELECT APPID AS ENTITY_ID, REPORTEDAT, SUM(INSTANCECOUNT) AS SUM_VALUE FROM
    //    INSTANCE_STATS WHERE REPORTEDAT  >= timestamp '2019-08-03T10:35:10.248Z' AND  REPORTEDAT  < timestamp
    //    '2019-08-10T10:35:10.248Z' AND ACCOUNTID = 'kmpySmUISimoRrJL6NL73w' GROUP BY ENTITY_ID, REPORTEDAT)
    //    INSTANCE_STATS GROUP BY ENTITY_ID, GRP_BY_TIME ORDER BY GRP_BY_TIME

    try {
      StringBuilder queryBuilder = new StringBuilder(350);
      queryBuilder.append("SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY SUM_VALUE) AS CNT, ");

      String[] values = extractTime(filters);
      String from = values[0];
      String to = values[1];

      String timeQuerySegment =
          instanceStatsDataFetcher.getGroupByTimeQueryWithGapFill(groupByTime, "REPORTEDAT", from, to);

      String groupByFieldName = getSqlFieldName(groupByEntity);
      queryBuilder.append(timeQuerySegment)
          .append(" AS GRP_BY_TIME, ENTITY_ID FROM (SELECT ")
          .append(groupByFieldName)
          .append(" AS ENTITY_ID, REPORTEDAT, SUM(INSTANCECOUNT) AS SUM_VALUE FROM INSTANCE_STATS WHERE ");
      if (isNotEmpty(filters)) {
        filters.forEach(filter -> {
          String sqlFilter = getSqlFilter(filter);
          if (isNotEmpty(sqlFilter)) {
            queryBuilder.append(sqlFilter);
            queryBuilder.append(" AND ");
          }
        });
      }

      queryBuilder.append(" REPORTEDAT ");
      addTimeQuery(queryBuilder, QLTimeOperator.AFTER, from);
      queryBuilder.append(" AND REPORTEDAT ");
      addTimeQuery(queryBuilder, QLTimeOperator.BEFORE, to);
      queryBuilder.append(" AND ACCOUNTID = '")
          .append(accountId)
          .append(
              "' GROUP BY ENTITY_ID, REPORTEDAT) INSTANCE_STATS GROUP BY ENTITY_ID, GRP_BY_TIME ORDER BY GRP_BY_TIME");

      List<QLStackedTimeSeriesDataPoint> dataPoints = new ArrayList<>();
      executeStackedTimeSeriesQuery(accountId, queryBuilder.toString(), dataPoints, groupByEntity);
      return QLStackedTimeSeriesData.builder().data(dataPoints).build();
    } catch (Exception ex) {
      throw new WingsException("Error while getting time series data", ex);
    }
  }

  public QLStackedTimeSeriesData getTimeSeriesAggregatedDataUsingNewAggregators(String accountId,
      QLNoOpAggregateFunction aggregateFunction, List<QLInstanceFilter> filters, QLTimeSeriesAggregation groupByTime,
      QLInstanceEntityAggregation groupByEntity) {
    try {
      Instant[] intervalInstants = extractTimestamps(filters);
      Instant intervalStartTime = intervalInstants[0];
      Instant intervalEndTime = intervalInstants[1];
      //      log.info("getTimeSeriesAggregatedDataUsingNewAggregators Interval start time : [{}] , interval end time :
      //      [{}]",
      //          intervalStartTime.toString(), intervalEndTime.toString());

      // Find first partial interval end timestamp based on groupBy time aggregation type
      // This is also the start timestamp for rest of the complete interval
      Instant startingPartialIntervalEndTime =
          getNextIntervalStartTimestamp(intervalStartTime, groupByTime.getTimeAggregationType());

      List<QLStackedTimeSeriesDataPoint> dataPoints = new ArrayList<>();

      // perform complete interval metric aggregation, find out end timestamp for starting partial interval
      List<QLStackedTimeSeriesDataPoint> completeDataPoints =
          doIntervalAggregation(accountId, filters, groupByTime, groupByEntity,
              startingPartialIntervalEndTime.toString(), intervalEndTime.toString(), COMPLETE_AGGREGATION_INTERVAL);

      // If complete interval start time is not equal to partial interval end time
      // => Complete interval = partial starting interval + N * complete interval (including possibly ending partial
      // interval) Need to calculate data point for the starting partial interval
      if (!startingPartialIntervalEndTime.equals(intervalStartTime)) {
        // List will always contain a single aggregated data point
        List<QLStackedTimeSeriesDataPoint> startingPartialIntervalDataPoints =
            doIntervalAggregation(accountId, filters, groupByTime, groupByEntity, intervalStartTime.toString(),
                startingPartialIntervalEndTime.toString(), PARTIAL_AGGREGATION_INTERVAL);

        // Get the display timestamp for the partial interval which is basically
        // the starting timestamp of the interpolated complete interval of this partial interval
        Instant partialIntervalTimestamp =
            getCurrentIntervalStartTimestamp(intervalStartTime, groupByTime.getTimeAggregationType());

        QLStackedTimeSeriesDataPoint partialIntervalDataPoint = startingPartialIntervalDataPoints.get(0);
        partialIntervalDataPoint.setTime(partialIntervalTimestamp.toEpochMilli());

        dataPoints.add(partialIntervalDataPoint);
      }

      dataPoints.addAll(completeDataPoints);
      return QLStackedTimeSeriesData.builder().data(dataPoints).build();
    } catch (Exception ex) {
      throw new WingsException("Error while getting time series data", ex);
    }
  }

  private List<QLStackedTimeSeriesDataPoint> doIntervalAggregation(String accountId, List<QLInstanceFilter> filters,
      QLTimeSeriesAggregation groupByTime, QLInstanceEntityAggregation groupByEntity, String intervalStartTimestamp,
      String intervalEndTimestamp, String aggregationIntervalType) {
    /*
    Sample complete interval query :
    SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY INSTANCECOUNT) AS CNT, APPID AS ENTITY_ID, WEEKTIMESTAMP AS
    GRP_BY_TIME FROM INSTANCE_STATS_DAY WHERE  REPORTEDAT  >= timestamp '2021-01-11T00:00:00Z' AND REPORTEDAT  <
    timestamp '2021-01-17T21:01:36.059Z' AND ACCOUNTID = 'kmpySmUISimoRrJL6NL73w' GROUP BY ENTITY_ID, WEEKTIMESTAMP
    ORDER BY WEEKTIMESTAMP;

    Sample PARTIAL interval query :
    SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY CNT) AS CNT, ENTITY_ID FROM (SELECT PERCENTILE_DISC(0.50)
    WITHIN GROUP (ORDER BY INSTANCECOUNT) AS CNT, APPID AS ENTITY_ID, REPORTEDAT AS GRP_BY_TIME FROM INSTANCE_STATS_HOUR
    WHERE  REPORTEDAT  >= timestamp '2021-01-10T21:01:36.059Z' AND REPORTEDAT  < timestamp '2021-01-11T00:00:00Z' AND
    ACCOUNTID = 'kmpySmUISimoRrJL6NL73w' GROUP BY ENTITY_ID, REPORTEDAT ORDER BY REPORTEDAT) INSTANCECOUNT GROUP BY
    ENTITY_ID

    COMPLETE interval query format :
    "SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY INSTANCECOUNT) AS CNT, $ENTITY_ID AS ENTITY_ID,
    $GROUP_BY_TIME_FIELD AS GROUP_BY_TIME FROM $TABLE_NAME "
    + "WHERE $FILTERS "
    + "AND REPORTEDAT >= TIMESTAMP '$START_TIMESTAMP' AND REPORTEDAT < TIMESTAMP '$END_TIMESTAMP "
    + "GROUP BY $GROUP_BY_TIME_FIELD, ENTITY_ID "
    + "ORDER BY $GROUP_BY_TIME_FIELD;";
     */

    try {
      StringBuilder queryBuilder = new StringBuilder(500);

      if (PARTIAL_AGGREGATION_INTERVAL.equals(aggregationIntervalType)) {
        queryBuilder.append("SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY CNT) AS CNT, ENTITY_ID FROM (");
      }

      queryBuilder.append("SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY INSTANCECOUNT) AS CNT, ");

      String groupByFieldName = getSqlFieldName(groupByEntity);
      String groupByTimeFieldName = getGroupByTimeField(groupByTime, aggregationIntervalType);
      String tableName = getTableName(groupByTime.getTimeAggregationType(), aggregationIntervalType);

      queryBuilder.append(groupByFieldName)
          .append(" AS ENTITY_ID, ")
          .append(groupByTimeFieldName)
          .append(" AS GRP_BY_TIME FROM ")
          .append(tableName)
          .append(" WHERE ");

      if (isNotEmpty(filters)) {
        filters.forEach(filter -> {
          String sqlFilter = getSqlFilter(filter);
          if (isNotEmpty(sqlFilter)) {
            queryBuilder.append(sqlFilter);
            queryBuilder.append(" AND ");
          }
        });
      }

      queryBuilder.append(" REPORTEDAT ");
      addTimeQuery(queryBuilder, QLTimeOperator.AFTER, intervalStartTimestamp);
      queryBuilder.append(" AND REPORTEDAT ");
      addTimeQuery(queryBuilder, QLTimeOperator.BEFORE, intervalEndTimestamp);
      queryBuilder.append(" AND ACCOUNTID = '")
          .append(accountId)
          .append("' GROUP BY ENTITY_ID, ")
          .append(groupByTimeFieldName)
          .append(" ORDER BY ")
          .append(groupByTimeFieldName);

      if (PARTIAL_AGGREGATION_INTERVAL.equals(aggregationIntervalType)) {
        queryBuilder.append(") INSTANCECOUNT GROUP BY ENTITY_ID");
      }

      List<QLStackedTimeSeriesDataPoint> dataPoints = new ArrayList<>();
      if (COMPLETE_AGGREGATION_INTERVAL.equals(aggregationIntervalType)) {
        executeStackedTimeSeriesQuery(accountId, queryBuilder.toString(), dataPoints, groupByEntity);
      } else {
        executeStackedTimeSeriesQueryForPartialInterval(accountId, queryBuilder.toString(), dataPoints, groupByEntity);
      }

      return dataPoints;
    } catch (Exception ex) {
      throw new WingsException("Error while getting time series data", ex);
    }
  }

  private String getGroupByTimeField(QLTimeSeriesAggregation groupByTime, String aggregationIntervalType) {
    QLTimeAggregationType type = groupByTime.getTimeAggregationType();
    if (COMPLETE_AGGREGATION_INTERVAL.equals(aggregationIntervalType)) {
      if (QLTimeAggregationType.MONTH.equals(type)) {
        return Constants.MONTH_TIMESTAMP_COL_NAME;
      }
      if (QLTimeAggregationType.WEEK.equals(type)) {
        return Constants.WEEK_TIMESTAMP_COL_NAME;
      }
    }
    return Constants.REPORTED_AT_COL_NAME;
  }

  private String getTableName(QLTimeAggregationType aggregationType, String aggregationIntervalType) {
    String tableName = "";
    if (PARTIAL_AGGREGATION_INTERVAL.equals(aggregationIntervalType)) {
      tableName = INSTANCE_STATS_HOUR_TABLE_NAME;
      if (aggregationType.equals(QLTimeAggregationType.HOUR)) {
        tableName = INSTANCE_STATS_TABLE_NAME;
      }
    }

    if (COMPLETE_AGGREGATION_INTERVAL.equals(aggregationIntervalType)) {
      tableName = INSTANCE_STATS_DAY_TABLE_NAME;
      if (aggregationType.equals(QLTimeAggregationType.HOUR)) {
        tableName = INSTANCE_STATS_HOUR_TABLE_NAME;
      }
    }

    return tableName;
  }

  private String[] extractTime(List<QLInstanceFilter> filters) {
    String from = null;
    String to = null;

    if (isNotEmpty(filters)) {
      for (QLInstanceFilter filter : filters) {
        QLTimeFilter createdAt = filter.getCreatedAt();
        if (createdAt == null) {
          continue;
        }

        QLTimeOperator operator = createdAt.getOperator();
        if (operator == null) {
          throw new WingsException("Time operator should have operator set");
        }

        switch (operator) {
          case BEFORE:
            to = Instant.ofEpochMilli((Long) createdAt.getValue()).toString();
            break;
          case AFTER:
            from = Instant.ofEpochMilli((Long) createdAt.getValue()).toString();
            break;
          default:
            throw new WingsException("Time operator " + operator.name() + " not supported.");
        }
      }
    }

    if (isEmpty(from) && isEmpty(to)) {
      log.info("No time filter set in Instance time series stats query. Setting default to 7 days");
      Instant now = Instant.now();
      from = now.minus(7, ChronoUnit.DAYS).toString();
      to = now.toString();
    } else if (isEmpty(from)) {
      from = Instant.parse(to).minus(7, ChronoUnit.DAYS).toString();
    } else if (isEmpty(to)) {
      to = Instant.parse(from).plus(7, ChronoUnit.DAYS).toString();
    }
    return new String[] {from, to};
  }

  private Instant[] extractTimestamps(List<QLInstanceFilter> filters) {
    Instant from = null;
    Instant to = null;

    if (isNotEmpty(filters)) {
      for (QLInstanceFilter filter : filters) {
        QLTimeFilter createdAt = filter.getCreatedAt();
        if (createdAt == null) {
          continue;
        }

        QLTimeOperator operator = createdAt.getOperator();
        if (operator == null) {
          throw new WingsException("Time operator should have operator set");
        }

        switch (operator) {
          case BEFORE:
            to = Instant.ofEpochMilli((Long) createdAt.getValue());
            break;
          case AFTER:
            from = Instant.ofEpochMilli((Long) createdAt.getValue());
            break;
          default:
            throw new WingsException("Time operator " + operator.name() + " not supported.");
        }
      }
    }

    if (from == null && to == null) {
      log.info("No time filter set in Instance time series stats query. Setting default to 7 days");
      Instant now = Instant.now();
      from = now.minus(7, ChronoUnit.DAYS);
      to = now;
    } else if (from == null) {
      from = to.minus(7, ChronoUnit.DAYS);
    } else if (to == null) {
      to = from.plus(7, ChronoUnit.DAYS);
    }
    return new Instant[] {from, to};
  }

  private Instant getNextIntervalStartTimestamp(Instant instant, QLTimeAggregationType aggregationType) {
    if (QLTimeAggregationType.HOUR.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getNextNearestWholeHourUTC(instant.toEpochMilli()));
    }
    if (QLTimeAggregationType.DAY.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getNextNearestWholeDayUTC(instant.toEpochMilli()));
    }
    if (QLTimeAggregationType.WEEK.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getNextNearestWeekBeginningTimestamp(instant.toEpochMilli()));
    }
    if (QLTimeAggregationType.MONTH.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getNextNearestMonthBeginningTimestamp(instant.toEpochMilli()));
    }

    return instant;
  }

  private Instant getCurrentIntervalStartTimestamp(Instant instant, QLTimeAggregationType aggregationType) {
    if (QLTimeAggregationType.HOUR.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getPrevWholeHourUTC(instant.toEpochMilli()));
    }
    if (QLTimeAggregationType.DAY.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getPrevNearestWholeDayUTC(instant.toEpochMilli()));
    }
    if (QLTimeAggregationType.WEEK.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getPrevNearestWeekBeginningTimestamp(instant.toEpochMilli()));
    }
    if (QLTimeAggregationType.MONTH.equals(aggregationType)) {
      return DateUtils.toInstant(DateUtils.getBeginningTimestampOfMonth(instant.toEpochMilli()));
    }

    return instant;
  }

  public QLTimeSeriesData getTimeSeriesData(String accountId, QLNoOpAggregateFunction aggregateFunction,
      List<QLInstanceFilter> filters, QLTimeSeriesAggregation groupByTime) {
    //    select percentile_disc(0.95) within group (order by sum_value) as percent, time_bucket_gapfill('1
    //    hours',reportedat) AS grp_by_time from (select reportedat, sum(INSTANCECOUNT) as sum_value from INSTANCE_STATS
    //    group by reportedat) INSTANCE_STATS group by grp_by_time order by grp_by_time

    try {
      StringBuilder queryBuilder = new StringBuilder(302);
      queryBuilder.append("SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY SUM_VALUE) AS CNT, ");
      String[] values = extractTime(filters);
      String from = values[0];
      String to = values[1];

      String timeQuerySegment =
          instanceStatsDataFetcher.getGroupByTimeQueryWithGapFill(groupByTime, "REPORTEDAT", from, to);

      queryBuilder.append(timeQuerySegment)
          .append(
              " AS GRP_BY_TIME FROM (SELECT REPORTEDAT, SUM(INSTANCECOUNT) AS SUM_VALUE FROM INSTANCE_STATS WHERE ");
      if (isNotEmpty(filters)) {
        filters.forEach(filter -> {
          String sqlFilter = getSqlFilter(filter);
          if (isNotEmpty(sqlFilter)) {
            queryBuilder.append(sqlFilter);
            queryBuilder.append(" AND ");
          }
        });
      }

      queryBuilder.append(" REPORTEDAT ");
      addTimeQuery(queryBuilder, QLTimeOperator.AFTER, from);
      queryBuilder.append(" AND REPORTEDAT ");
      addTimeQuery(queryBuilder, QLTimeOperator.BEFORE, to);
      queryBuilder.append(" AND ACCOUNTID = '")
          .append(accountId)
          .append("' GROUP BY REPORTEDAT) INSTANCE_STATS GROUP BY GRP_BY_TIME ORDER BY GRP_BY_TIME");

      List<QLTimeSeriesDataPoint> dataPoints = new ArrayList<>();
      executeTimeSeriesQuery(accountId, queryBuilder.toString(), dataPoints);
      return QLTimeSeriesData.builder().dataPoints(dataPoints).build();
    } catch (Exception ex) {
      throw new WingsException("Error while getting time series data", ex);
    }
  }

  private String getSqlFieldName(QLInstanceEntityAggregation aggregation) {
    switch (aggregation) {
      case Application:
        return "APPID";
      case Service:
        return "SERVICEID";
      case Environment:
        return "ENVID";
      case CloudProvider:
        return "CLOUDPROVIDERID";
      case InstanceType:
        return "INSTANCETYPE";
      default:
        throw new WingsException("Unknown aggregation type" + aggregation);
    }
  }

  private void executeTimeSeriesQuery(String accountId, String query, List<QLTimeSeriesDataPoint> dataPoints) {
    int retryCount = 0;
    while (retryCount < MAX_RETRY) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        while (resultSet != null && resultSet.next()) {
          QLTimeSeriesDataPointBuilder dataPointBuilder = QLTimeSeriesDataPoint.builder();
          dataPointBuilder.value(resultSet.getInt("CNT"));
          dataPointBuilder.time(resultSet.getTimestamp("GRP_BY_TIME").getTime());
          dataPoints.add(dataPointBuilder.build());
        }
        return;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to execute query=[{}],accountId=[{}]", query, accountId, e);
        } else {
          log.warn("Failed to execute query=[{}],accountId=[{}],retryCount=[{}]", query, accountId, retryCount);
        }
        retryCount++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  public void purgeOldInstances() {
    if (!timeScaleDBService.isValid()) {
      log.info("Skipping purge of old instances from time scale db since time scale db is not initialized");
      return;
    }

    int retryCount = 0;
    TimeScaleDBConfig timeScaleDBConfig = timeScaleDBService.getTimeScaleDBConfig();
    int expiryInDays = timeScaleDBConfig != null && timeScaleDBConfig.getInstanceDataRetentionDays() > 0
        ? timeScaleDBConfig.getInstanceDataRetentionDays()
        : DEFAULT_EXPIRY_IN_DAYS;
    String query = new StringBuilder("SELECT drop_chunks(interval '")
                       .append(expiryInDays)
                       .append(" days', 'instance_stats')")
                       .toString();

    while (retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        statement.execute(query);
        return;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to execute query=[{}]", query, e);
        } else {
          log.warn("Failed to execute query=[{}], retryCount=[{}]", query, retryCount);
        }
        retryCount++;
      }
    }
  }

  private void executeStackedTimeSeriesQuery(String accountId, String query,
      List<QLStackedTimeSeriesDataPoint> stackedTimeSeriesDataPoints, QLInstanceEntityAggregation groupBy) {
    int retryCount = 0;
    String entityType = groupBy.name();
    Multimap<String, QLReference> idRefMap = HashMultimap.create();
    while (retryCount < MAX_RETRY) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        long previousTime = -1L;
        List<QLDataPoint> dataPointList = new ArrayList<>();
        while (resultSet != null && resultSet.next()) {
          long timestamp = resultSet.getTimestamp("GRP_BY_TIME").getTime();
          if (previousTime != timestamp) {
            previousTime = timestamp;
            dataPointList = new ArrayList<>();
            QLStackedTimeSeriesDataPoint stackedDataPoint = new QLStackedTimeSeriesDataPoint();
            stackedDataPoint.setTime(timestamp);
            stackedDataPoint.setValues(dataPointList);
            stackedTimeSeriesDataPoints.add(stackedDataPoint);
          }

          QLReference qlReference = new QLReference();
          String entityId = resultSet.getString("ENTITY_ID");
          qlReference.setId(entityId);
          idRefMap.put(entityId, qlReference);
          qlReference.setType(entityType);
          QLDataPoint dataPoint = new QLDataPoint();
          dataPoint.setKey(qlReference);
          dataPoint.setValue(resultSet.getInt("CNT"));
          dataPointList.add(dataPoint);
        }

        NameResult nameResult = nameService.getNames(idRefMap.keySet(), entityType);
        idRefMap.keySet().forEach(key -> {
          String name = instanceStatsDataFetcher.getEntityName(nameResult, key, entityType);
          idRefMap.get(key).forEach(ref -> ref.setName(name));
        });

        return;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to execute query=[{}],accountId=[{}]", query, accountId, e);
        } else {
          log.warn("Failed to execute query=[{}],accountId=[{}],retryCount=[{}]", query, accountId, retryCount);
        }
        retryCount++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  private void executeStackedTimeSeriesQueryForPartialInterval(String accountId, String query,
      List<QLStackedTimeSeriesDataPoint> stackedTimeSeriesDataPoints, QLInstanceEntityAggregation groupBy) {
    int retryCount = 0;
    String entityType = groupBy.name();
    Multimap<String, QLReference> idRefMap = HashMultimap.create();
    while (retryCount < MAX_RETRY) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        List<QLDataPoint> dataPointList = new ArrayList<>();
        while (resultSet != null && resultSet.next()) {
          QLReference qlReference = new QLReference();
          String entityId = resultSet.getString("ENTITY_ID");
          qlReference.setId(entityId);
          idRefMap.put(entityId, qlReference);
          qlReference.setType(entityType);
          QLDataPoint dataPoint = new QLDataPoint();
          dataPoint.setKey(qlReference);
          dataPoint.setValue(resultSet.getInt("CNT"));
          dataPointList.add(dataPoint);
        }

        QLStackedTimeSeriesDataPoint stackedDataPoint = new QLStackedTimeSeriesDataPoint();
        // Set dummy timestamp for now, replace it with proper timestamp using starting timestamp
        stackedDataPoint.setTime(0L);
        stackedDataPoint.setValues(dataPointList);
        stackedTimeSeriesDataPoints.add(stackedDataPoint);

        NameResult nameResult = nameService.getNames(idRefMap.keySet(), entityType);
        idRefMap.keySet().forEach(key -> {
          String name = instanceStatsDataFetcher.getEntityName(nameResult, key, entityType);
          idRefMap.get(key).forEach(ref -> ref.setName(name));
        });

        return;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to execute query=[{}],accountId=[{}]", query, accountId, e);
        } else {
          log.warn("Failed to execute query=[{}],accountId=[{}],retryCount=[{}]", query, accountId, retryCount);
        }
        retryCount++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  private String getSqlFilter(QLInstanceFilter filter) {
    StringBuilder queryBuilder = new StringBuilder(56);
    boolean multipleCriteria = false;
    if (filter.getApplication() != null) {
      multipleCriteria = setMultipleCriteria(multipleCriteria, queryBuilder);
      queryBuilder.append(" APPID ");
      addIdQuery(queryBuilder, filter.getApplication());
    }

    if (filter.getCloudProvider() != null) {
      queryBuilder.append(" CLOUDPROVIDERID ");
      multipleCriteria = setMultipleCriteria(multipleCriteria, queryBuilder);
      addIdQuery(queryBuilder, filter.getCloudProvider());
    }

    if (filter.getEnvironment() != null) {
      queryBuilder.append(" ENVID ");
      multipleCriteria = setMultipleCriteria(multipleCriteria, queryBuilder);
      addIdQuery(queryBuilder, filter.getEnvironment());
    }

    if (filter.getService() != null) {
      queryBuilder.append(" SERVICEID ");
      multipleCriteria = setMultipleCriteria(multipleCriteria, queryBuilder);
      addIdQuery(queryBuilder, filter.getService());
    }

    if (filter.getInstanceType() != null) {
      setMultipleCriteria(multipleCriteria, queryBuilder);
      queryBuilder.append(" INSTANCETYPE = '");
      queryBuilder.append(filter.getInstanceType().name());
      queryBuilder.append('\'');
    }

    return queryBuilder.toString();
  }

  private boolean setMultipleCriteria(boolean multipleCriteria, StringBuilder queryBuilder) {
    if (multipleCriteria) {
      queryBuilder.append(" AND ");
    } else {
      multipleCriteria = true;
    }
    return multipleCriteria;
  }

  private void addTimeQuery(StringBuilder queryBuilder, QLTimeOperator operator, String timestamp) {
    switch (operator) {
      case EQUALS:
        queryBuilder.append(" = timestamp '");
        queryBuilder.append(timestamp);
        queryBuilder.append('\'');
        break;
      case BEFORE:
        queryBuilder.append(" < timestamp '");
        queryBuilder.append(timestamp);
        queryBuilder.append('\'');
        break;
      case AFTER:
        queryBuilder.append(" >= timestamp '");
        queryBuilder.append(timestamp);
        queryBuilder.append('\'');
        break;
      default:
        throw new RuntimeException("Time operator not supported" + timestamp);
    }
  }

  private void addIdQuery(StringBuilder queryBuilder, QLIdFilter filter) {
    switch (filter.getOperator()) {
      case EQUALS:
        queryBuilder.append(" = '");
        queryBuilder.append(filter.getValues()[0]);
        queryBuilder.append('\'');
        break;
      case IN:
        queryBuilder.append(" IN (");
        instanceStatsDataFetcher.generateSqlInQuery(queryBuilder, filter.getValues());
        queryBuilder.append(')');
        break;
      default:
        throw new WingsException("Id operator not supported" + filter.getOperator());
    }
  }
}
