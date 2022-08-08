/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.event.timeseries.processor.EventProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.Migration;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.EntityType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot.AggregateCount;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot.InstanceStatsSnapshotKeys;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo;
import software.wings.service.intfc.instance.stats.InstanceStatService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.mongodb.morphia.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class UpdateCorruptedInstanceStatsMigration implements Migration {
  @Inject private InstanceStatService instanceStatService;
  @Inject private MongoPersistence mongoPersistence;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private DataFetcherUtils utils;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;

  // time of 06.03.2022 00:00:00 GMT
  private static final long FROM = 1646524800000L;
  // time of 25.03.2022 00:00:00 GMT
  private static final long UNTIL = 1648166400000L;

  private static final int INFRA_INSTANCE_BATCH_SIZE = 50;
  private static final int INSTANCE_STATS_BATCH_SIZE = 500;

  public static final int MAX_RETRY = 3;
  public static final String DELETE_INSTANCE_DATA_POINTS_INTERVAL = "DELETE FROM INSTANCE_STATS WHERE ACCOUNTID = ? "
      + "AND REPORTEDAT > ? AND REPORTEDAT < ?";
  private final String DEBUG_LINE = "UPDATE_CORRUPTED_INSTANCE_STATS_MIGRATION: ";

  @Override
  public void migrate() {
    log.info("Running UpdateCorruptedInstanceStatsMigration");
    /*
      "RDIpim-aQpiTdqZ5R2R18g"  --> SoulCycle
      "dt8jC4_3QZ2OPM7drpLljQ"  --> Lands End
      "alIesC1LR0W2mozD2rWEhg"  --> Arch
      "XICOBc_qRa2PJmVaWOx-cQ"  --> AutomationOne(QA)
      "zEaak-FLS425IEO7OLzMUg"  --> Harness io (QA)
   */
    Set<String> firstReleaseConsideredAccount = new HashSet<>(asList("RDIpim-aQpiTdqZ5R2R18g", "dt8jC4_3QZ2OPM7drpLljQ",
        "alIesC1LR0W2mozD2rWEhg", "XICOBc_qRa2PJmVaWOx-cQ", "zEaak-FLS425IEO7OLzMUg"));

    // Todo: change logic to do migration for all accounts in next release
    Set<String> affectedAccounts = deleteDuplicateInstanceMigration(firstReleaseConsideredAccount);
    updateCorruptedInstanceStats(affectedAccounts);
    log.info("Completed UpdateCorruptedInstanceStatsMigration");
  }

  private Set<String> deleteDuplicateInstanceMigration(Set<String> firstReleaseConsideredAccount) {
    Set<String> affectedAccounts = new HashSet<>();
    try (HIterator<InfrastructureMapping> infraMappingIter =
             new HIterator<>(mongoPersistence.createQuery(InfrastructureMapping.class, excludeAuthority)
                                 .field(InfrastructureMappingKeys.accountId)
                                 .notIn(firstReleaseConsideredAccount)
                                 .filter(InfrastructureMappingKeys.deploymentType, "HELM")
                                 .project("_id", true)
                                 .project(InfrastructureMappingKeys.accountId, true)
                                 .project(InfrastructureMappingKeys.appId, true)
                                 .disableValidation()
                                 .fetch())) {
      int updated = 0;
      BulkWriteOperation instanceWriteOperation =
          mongoPersistence.getCollection(Instance.class).initializeUnorderedBulkOperation();
      while (infraMappingIter.hasNext()) {
        InfrastructureMapping infraMapping = infraMappingIter.next();
        affectedAccounts.add(infraMapping.getAccountId());
        if (featureFlagService.isEnabled(FeatureName.KEEP_PT_AFTER_K8S_DOWNSCALE, infraMapping.getAccountId())) {
          continue;
        }

        HashMap<String, Object> query = new HashMap<>();
        query.put(InstanceKeys.appId, infraMapping.getAppId());
        query.put(InstanceKeys.infraMappingId, infraMapping.getUuid());
        query.put(InstanceKeys.createdAt, new BasicDBObject(ImmutableMap.of("$gte", FROM, "$lt", UNTIL)));
        query.put(InstanceKeys.lastWorkflowExecutionId, null);
        query.put(InstanceKeys.lastDeployedById, "AUTO_SCALE");

        instanceWriteOperation.find(new BasicDBObject(query)).remove();
        updated++;

        if (updated >= INFRA_INSTANCE_BATCH_SIZE) {
          log.info("Delete corrupted instances for {} infra mappings: {}", updated, instanceWriteOperation.execute());
          sleep(Duration.ofMillis(100)); // give some relax time
          instanceWriteOperation = mongoPersistence.getCollection(Instance.class).initializeUnorderedBulkOperation();
          updated = 0;
        }
      }

      if (updated != 0) {
        log.info("Delete corrupted instances for {} infra mappings: {}", updated, instanceWriteOperation.execute());
      }
    }

    return affectedAccounts;
  }

  private void updateCorruptedInstanceStats(Set<String> affectedAccounts) {
    affectedAccounts.forEach(accountId -> {
      try {
        int updated = 0;
        BulkWriteOperation bulkWriteOperation =
            mongoPersistence.getCollection(InstanceStatsSnapshot.class).initializeUnorderedBulkOperation();

        List<InstanceStatsSnapshot> instanceStats =
            instanceStatService.aggregate(accountId, Instant.ofEpochMilli(FROM), Instant.ofEpochMilli(UNTIL));
        for (InstanceStatsSnapshot statsSnapshot : instanceStats) {
          List<Instance> instances = getInstances(accountId, statsSnapshot.getTimestamp());
          boolean successful = deleteInstanceStatsDataPointsFromTsDb(accountId, statsSnapshot.getTimestamp());

          /*
           get consumed by
           io.harness.event.timeseries.processor.instanceeventprocessor.InstanceEventProcessor.processEvent where tsdb
           datapoint timestamp is referred from statsSnapshot.getTimestamp().toEpochMilli()
           */
          log.info(StringUtils.join(
              DEBUG_LINE, format("published instance event for %s", statsSnapshot.getTimestamp().toString())));
          if (successful) {
            usageMetricsEventPublisher.publishInstanceTimeSeries(
                accountId, statsSnapshot.getTimestamp().toEpochMilli(), instances);
          }
          InstanceStatsSnapshot updatedInstanceStats =
              mapInstanceStatsSnapshot(instances, accountId, statsSnapshot.getTimestamp());

          if (updatedInstanceStats != null) {
            bulkWriteOperation.find(new BasicDBObject("_id", statsSnapshot.getUuid()))
                .updateOne(new BasicDBObject("$set",
                    new Document(ImmutableMap.of(InstanceStatsSnapshotKeys.aggregateCounts,
                        getAggregateCountDBList(updatedInstanceStats.getAggregateCounts()),
                        InstanceStatsSnapshotKeys.total, updatedInstanceStats.getTotal()))));

            updated++;

            if (updated >= INSTANCE_STATS_BATCH_SIZE) {
              log.info(
                  "Updated {} instance stats four account id {}: {}", updated, accountId, bulkWriteOperation.execute());
              bulkWriteOperation =
                  mongoPersistence.getCollection(InstanceStatsSnapshot.class).initializeUnorderedBulkOperation();
              updated = 0;
            }
          }
        }

        if (updated != 0) {
          log.info(
              "Updated {} instance stats four account id {}: {}", updated, accountId, bulkWriteOperation.execute());
        }

      } catch (Exception ex) {
        log.error(StringUtils.join(
                      DEBUG_LINE, format("Error updating corrupted instance stats for accountId %s ", accountId)),
            ex);
      }

      log.info(StringUtils.join(
          DEBUG_LINE, format("Updated corrupted instance stats successfully for accountId %s ", accountId)));
    });
  }

  private boolean deleteInstanceStatsDataPointsFromTsDb(String accountId, Instant instant) {
    if (isBlank(accountId)) {
      return true;
    }
    int retry = 0;
    boolean successfulOperation = false;
    final Timestamp from = Timestamp.from(instant.minusSeconds(300));
    final Timestamp until = Timestamp.from(instant.plusSeconds(300));
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement = dbConnection.prepareStatement(DELETE_INSTANCE_DATA_POINTS_INTERVAL)) {
        fetchStatement.setString(1, accountId);
        fetchStatement.setTimestamp(2, from, utils.getDefaultCalendar());
        fetchStatement.setTimestamp(3, until, utils.getDefaultCalendar());
        fetchStatement.execute();
        successfulOperation = true;
      } catch (Exception exception) {
        if (retry >= MAX_RETRY) {
          log.error(StringUtils.join(DEBUG_LINE,
              format(
                  "MAX RETRY FAILURE : Failed to delete instanceStats data points from : %s , until : %s for accountId : %s",
                  from.toString(), until.toString(), accountId),
              exception));
          return false;
        }
        log.error(StringUtils.join(DEBUG_LINE,
            format("Failed to delete instanceStats data points from : %s , until : %s for accountId : %s , retry : %d ",
                from.toString(), until.toString(), accountId, retry),
            exception));
        retry++;
      }
    }
    return successfulOperation;
  }

  private Map<Timestamp, Map<String, TimeSeriesBatchEventInfo.DataPoint>> processResultSetToCreateDataPoints(
      ResultSet resultSet) throws SQLException {
    Map<Timestamp, List<TimeSeriesBatchEventInfo.DataPoint>> resultMap = new HashMap<>();
    while (resultSet.next()) {
      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(EventProcessor.APPID, resultSet.getString(EventProcessor.APPID));
      dataMap.put(EventProcessor.SERVICEID, resultSet.getString(EventProcessor.SERVICEID));
      dataMap.put(EventProcessor.ENVID, resultSet.getString(EventProcessor.ENVID));
      dataMap.put(EventProcessor.CLOUDPROVIDERID, resultSet.getString(EventProcessor.CLOUDPROVIDERID));
      dataMap.put(EventProcessor.INSTANCETYPE, resultSet.getString(EventProcessor.INSTANCETYPE));
      dataMap.put(EventProcessor.ARTIFACTID, resultSet.getString(EventProcessor.ARTIFACTID));
      dataMap.put(EventProcessor.INSTANCECOUNT, resultSet.getString(EventProcessor.INSTANCECOUNT));
      dataMap.put(EventProcessor.REPORTEDAT, resultSet.getTimestamp(EventProcessor.REPORTEDAT));
      Timestamp reportedAt = resultSet.getTimestamp(EventProcessor.REPORTEDAT);
      if (resultMap.containsKey(reportedAt)) {
        resultMap.get(reportedAt).add(TimeSeriesBatchEventInfo.DataPoint.builder().data(dataMap).build());
      } else {
        resultMap.put(
            reportedAt, Collections.singletonList(TimeSeriesBatchEventInfo.DataPoint.builder().data(dataMap).build()));
      }
    }
    return null;
  }

  private List<Instance> getInstances(String accountId, Instant toTime) {
    if (isBlank(accountId)) {
      return null;
    }
    try {
      Query<Instance> containerFetchInstancesQuery = mongoPersistence.createQuery(Instance.class, excludeAuthority)
                                                         .filter("accountId", accountId)
                                                         .field("createdAt")
                                                         .lessThanOrEq(toTime.toEpochMilli());
      containerFetchInstancesQuery.and(
          containerFetchInstancesQuery.or(containerFetchInstancesQuery.criteria("isDeleted").equal(false),
              containerFetchInstancesQuery.criteria("deletedAt").greaterThanOrEq(toTime.toEpochMilli())));

      return containerFetchInstancesQuery.asList();
    } catch (Exception e) {
      log.error(
          StringUtils.join(DEBUG_LINE, format("Error getting unique instance count for accountId %s", accountId)), e);
      return null;
    }
  }

  public InstanceStatsSnapshot mapInstanceStatsSnapshot(List<Instance> instances, String accountId, Instant instant) {
    Collection<AggregateCount> appCounts = aggregateByApp(instances);
    List<AggregateCount> aggregateCounts = new ArrayList<>(appCounts);

    return new InstanceStatsSnapshot(instant, accountId, aggregateCounts);
  }

  private Collection<AggregateCount> aggregateByApp(List<Instance> instances) {
    Map<String, AggregateCount> appCounts = new HashMap<>();
    for (Instance instance : instances) {
      AggregateCount appCount = appCounts.computeIfAbsent(instance.getAppId(),
          appId -> new AggregateCount(EntityType.APPLICATION, instance.getAppName(), instance.getAppId(), 0));
      appCount.incrementCount(1);
    }
    return appCounts.values();
  }

  private BasicDBList getAggregateCountDBList(List<AggregateCount> aggregateCounts) {
    BasicDBList basicDBList = new BasicDBList();

    for (final AggregateCount aggregateCount : aggregateCounts) {
      basicDBList.add(new BasicDBObject(ImmutableMap.of("entityType", aggregateCount.getEntityType().name(), "name",
          aggregateCount.getName(), "id", aggregateCount.getId(), "count", aggregateCount.getCount())));
    }

    return basicDBList;
  }
}