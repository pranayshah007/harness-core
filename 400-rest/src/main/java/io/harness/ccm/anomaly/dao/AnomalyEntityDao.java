/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.entities.AnomalyDetectionModel;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomaliesDataTableSchema;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomalyEntityBuilder;
import io.harness.ccm.anomaly.entities.TimeGranularity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.schema.type.aggregation.anomaly.QLAnomalyFeedback;

import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.DeleteQuery;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class AnomalyEntityDao {
  static final int MAX_RETRY = 3;
  static final int BATCH_SIZE = 50;

  @Inject @Autowired private TimeScaleDBService dbService;

  public AnomalyEntity update(AnomalyEntity anomaly) {
    String updateStatement = getUpdateQuery(anomaly);
    log.info("Prepared Statement for update AnomalyEntity: {} ", updateStatement);

    int retryCount = 0;
    int count = 0;
    while (retryCount < MAX_RETRY) {
      try (Connection dbConnection = dbService.getDBConnection();
           Statement statement = dbConnection.createStatement()) {
        count = statement.executeUpdate(updateStatement);
        log.info(" Update Query status : {} , after retry count : {}", count, retryCount + 1);
        if (count > 0) {
          return anomaly;
        }
      } catch (SQLException e) {
        retryCount++;
      }
    }
    return null;
  }

  private String getUpdateQuery(AnomalyEntity anomaly) {
    UpdateQuery query = new UpdateQuery(AnomaliesDataTableSchema.table);

    if (EmptyPredicate.isNotEmpty(anomaly.getId())) {
      query.addCondition(BinaryCondition.equalTo(AnomalyEntity.AnomaliesDataTableSchema.id, anomaly.getId()));
    } else {
      throw new InvalidArgumentsException("Update cannot be done since given anomaly doesn't contain id");
    }

    if (EmptyPredicate.isNotEmpty(anomaly.getAccountId())) {
      query.addCondition(BinaryCondition.equalTo(AnomaliesDataTableSchema.accountId, anomaly.getAccountId()));
    }

    if (EmptyPredicate.isNotEmpty(anomaly.getNote())) {
      query.addSetClause(AnomaliesDataTableSchema.note, anomaly.getNote());
    }

    if (anomaly.getFeedback() != null) {
      query.addCustomSetClause(AnomaliesDataTableSchema.feedBack, anomaly.getFeedback());
    }

    query.addSetClause(
        AnomaliesDataTableSchema.slackInstantNotification, ((Boolean) anomaly.isSlackInstantNotification()).toString());
    query.addCustomSetClause(
        AnomaliesDataTableSchema.slackDailyNotification, ((Boolean) anomaly.isSlackDailyNotification()).toString());
    query.addCustomSetClause(
        AnomaliesDataTableSchema.slackWeeklyNotification, ((Boolean) anomaly.isSlackWeeklyNotification()).toString());

    return query.validate().toString();
  }

  public List<AnomalyEntity> list(String queryStatement) {
    List<AnomalyEntity> listAnomalies = new ArrayList<>();
    boolean successfulRead = false;
    ResultSet resultSet = null;
    int retryCount = 0;
    while (!successfulRead && retryCount < MAX_RETRY) {
      try (Connection dbConnection = dbService.getDBConnection();
           PreparedStatement statement = dbConnection.prepareStatement(queryStatement)) {
        log.info("[RDA] Query Step 1/3 : Statement in AnomalyDao: {} ", queryStatement);
        resultSet = statement.executeQuery();
        listAnomalies = extractAnomaliesFromResultSet(resultSet);
        successfulRead = true;
      } catch (SQLException e) {
        retryCount++;
        log.info("[RDA] Query failed after retry count {} , Exception {}", retryCount, e);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return listAnomalies;
  }

  private List<AnomalyEntity> extractAnomaliesFromResultSet(ResultSet resultSet) throws SQLException {
    List<AnomalyEntity> listAnomalies = new ArrayList<>();

    log.info("[RDA] Query Step 2/2 : conversion of resultset into anomalyEntities");

    while (null != resultSet && resultSet.next()) {
      AnomalyEntityBuilder anomalyBuilder = AnomalyEntity.builder();
      for (AnomalyEntity.AnomaliesDataTableSchema.fields field : AnomalyEntity.AnomaliesDataTableSchema.getFields()) {
        switch (field) {
          case ANOMALY_TIME:
            anomalyBuilder.anomalyTime(resultSet.getTimestamp(field.getFieldName()).toInstant());
            break;
          case REPORTED_BY:
            anomalyBuilder.reportedBy(AnomalyDetectionModel.valueOf(resultSet.getString(field.getFieldName())));
            break;
          case FEED_BACK:
            if (resultSet.getString(field.getFieldName()) != null) {
              anomalyBuilder.feedback(QLAnomalyFeedback.valueOf(resultSet.getString(field.getFieldName())));
            } else {
              anomalyBuilder.feedback(QLAnomalyFeedback.NOT_RESPONDED);
            }
            break;
          case ID:
            anomalyBuilder.id(resultSet.getString(field.getFieldName()));
            break;
          case ACCOUNT_ID:
            anomalyBuilder.accountId(resultSet.getString(field.getFieldName()));
            break;
          case ACTUAL_COST:
            anomalyBuilder.actualCost(resultSet.getDouble(field.getFieldName()));
            break;
          case EXPECTED_COST:
            anomalyBuilder.expectedCost(resultSet.getDouble(field.getFieldName()));
            break;
          case NOTE:
            anomalyBuilder.note(resultSet.getString(field.getFieldName()));
            break;
          case ANOMALY_SCORE:
            anomalyBuilder.anomalyScore(resultSet.getDouble(field.getFieldName()));
            break;
          case CLUSTER_ID:
            anomalyBuilder.clusterId(resultSet.getString(field.getFieldName()));
            break;
          case NAMESPACE:
            anomalyBuilder.namespace(resultSet.getString(field.getFieldName()));
            break;
          case CLUSTER_NAME:
            anomalyBuilder.clusterName(resultSet.getString(field.getFieldName()));
            break;
          case WORKLOAD_NAME:
            anomalyBuilder.workloadName(resultSet.getString(field.getFieldName()));
            break;
          case WORKLOAD_TYPE:
            anomalyBuilder.workloadType(resultSet.getString(field.getFieldName()));
            break;
          case GCP_PRODUCT:
            anomalyBuilder.gcpProduct(resultSet.getString(field.getFieldName()));
            break;
          case GCP_PROJECT:
            anomalyBuilder.gcpProject(resultSet.getString(field.getFieldName()));
            break;
          case GCP_SKU_ID:
            anomalyBuilder.gcpSKUId(resultSet.getString(field.getFieldName()));
            break;
          case GCP_SKU_DESCRIPTION:
            anomalyBuilder.gcpSKUDescription(resultSet.getString(field.getFieldName()));
            break;
          case AZURE_SUBSCRIPTION:
            anomalyBuilder.azureSubscription(resultSet.getString(field.getFieldName()));
            break;
          case AZURE_RESOURCE_GROUP:
            anomalyBuilder.azureResourceGroup(resultSet.getString(field.getFieldName()));
            break;
          case AZURE_METER_CATEGORY:
            anomalyBuilder.azureMeterCategory(resultSet.getString(field.getFieldName()));
            break;
          case AWS_ACCOUNT:
            anomalyBuilder.awsAccount(resultSet.getString(field.getFieldName()));
            break;
          case AWS_SERVICE:
            anomalyBuilder.awsService(resultSet.getString(field.getFieldName()));
            break;
          case AWS_INSTANCE_TYPE:
            anomalyBuilder.awsInstanceType(resultSet.getString(field.getFieldName()));
            break;
          case AWS_USAGE_TYPE:
            anomalyBuilder.awsUsageType(resultSet.getString(field.getFieldName()));
            break;
          case SLACK_DAILY_NOTIFICATION:
            anomalyBuilder.slackDailyNotification(resultSet.getBoolean(field.getFieldName()));
            break;
          case SLACK_INSTANT_NOTIFICATION:
            anomalyBuilder.slackInstantNotification(resultSet.getBoolean(field.getFieldName()));
            break;
          case SLACK_WEEKLY_NOTIFICATION:
            anomalyBuilder.slackWeeklyNotification(resultSet.getBoolean(field.getFieldName()));
            break;
          case NEW_ENTITY:
            anomalyBuilder.newEntity(resultSet.getBoolean(field.getFieldName()));
            break;
          case REGION:
            break;
          case TIME_GRANULARITY:
            anomalyBuilder.timeGranularity(TimeGranularity.valueOf(resultSet.getString(field.getFieldName())));
            break;
          default:
            log.error("Unknown field : {} encountered while Resultset conversion in AnomalyDao", field);
        }
      }
      listAnomalies.add(anomalyBuilder.build());
    }

    return listAnomalies;
  }

  public void delete(List<String> ids, Instant date) {
    boolean successfulDelete = false;
    if (dbService.isValid()) {
      String queryStatement = getDeleteQuery(ids, date);
      log.info("[RDA] Deleting anomalies with query : [{}]", queryStatement);
      int retryCount = 0;
      while (!successfulDelete && retryCount < MAX_RETRY) {
        try (Connection dbConnection = dbService.getDBConnection();
             Statement statement = dbConnection.createStatement()) {
          statement.execute(queryStatement);
          successfulDelete = true;
        } catch (SQLException e) {
          log.error("[RDA] Failed to delete anomalies , retryCount=[{}], Exception: ", retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.error("[RDA] Not able to delete anomalies in timescale db(validity:{}) ", dbService.isValid());
    }
  }

  private String getDeleteQuery(List<String> ids, Instant date) {
    DeleteQuery query = new DeleteQuery(AnomalyEntity.AnomaliesDataTableSchema.table);
    query.addCondition(new InCondition(AnomalyEntity.AnomaliesDataTableSchema.id, ids));
    query.addCondition(
        BinaryCondition.equalTo(AnomalyEntity.AnomaliesDataTableSchema.anomalyTime, date.truncatedTo(ChronoUnit.DAYS)));
    return query.validate().toString();
  }

  public void insert(List<? extends AnomalyEntity> anomaliesList) {
    boolean successfulInsert = false;
    if (dbService.isValid() && !anomaliesList.isEmpty()) {
      int retryCount = 0;
      int index = 0;
      while (!successfulInsert && retryCount < MAX_RETRY) {
        try (Connection dbConnection = dbService.getDBConnection();
             Statement statement = dbConnection.createStatement()) {
          index = 0;
          for (AnomalyEntity anomaly : anomaliesList) {
            statement.addBatch(getInsertQuery(anomaly));
            index++;
            if (index % BATCH_SIZE == 0 || index == anomaliesList.size()) {
              log.debug("Prepared Statement in AnomalyEntityDao: {} ", statement);
              int[] count = statement.executeBatch();
              log.debug("Successfully inserted {} anomalies into timescaledb", IntStream.of(count).sum());
            }
          }
          successfulInsert = true;
        } catch (SQLException e) {
          log.error(
              "Failed to save anomalies data,[{}],retryCount=[{}], Exception: ", anomaliesList.size(), retryCount, e);
          retryCount++;
        }
      }
      if (!successfulInsert) {
        throw new InvalidArgumentsException("Not being able to write anomalies into db.");
      }
    } else {
      log.warn("Not able to write {} anomalies to timescale db(validity:{}) for account", anomaliesList.size(),
          dbService.isValid());
    }
  }

  private String getInsertQuery(AnomalyEntity anomaly) {
    return new InsertQuery(AnomaliesDataTableSchema.table)
               .addColumn(AnomaliesDataTableSchema.id, anomaly.getId())
               .addColumn(AnomaliesDataTableSchema.accountId, anomaly.getAccountId())
               .addColumn(AnomaliesDataTableSchema.actualCost, anomaly.getActualCost())
               .addColumn(AnomaliesDataTableSchema.expectedCost, anomaly.getExpectedCost())
               .addColumn(AnomaliesDataTableSchema.anomalyTime, anomaly.getAnomalyTime())
               .addColumn(AnomaliesDataTableSchema.timeGranularity, anomaly.getTimeGranularity().toString())
               .addColumn(AnomaliesDataTableSchema.clusterId, anomaly.getClusterId())
               .addColumn(AnomaliesDataTableSchema.clusterName, anomaly.getClusterName())
               .addColumn(AnomaliesDataTableSchema.namespace, anomaly.getNamespace())
               .addColumn(AnomaliesDataTableSchema.workloadType, anomaly.getWorkloadType())
               .addColumn(AnomaliesDataTableSchema.workloadName, anomaly.getWorkloadName())
               .addColumn(AnomaliesDataTableSchema.region, anomaly.getRegion())
               .addColumn(AnomaliesDataTableSchema.gcpProduct, anomaly.getGcpProduct())
               .addColumn(AnomaliesDataTableSchema.gcpProject, anomaly.getGcpProject())
               .addColumn(AnomaliesDataTableSchema.gcpSkuId, anomaly.getGcpSKUId())
               .addColumn(AnomaliesDataTableSchema.gcpSkuDescription, anomaly.getGcpSKUDescription())
               .addColumn(AnomaliesDataTableSchema.azureSubscription, anomaly.getAzureSubscription())
               .addColumn(AnomaliesDataTableSchema.azureResourceGroup, anomaly.getAzureResourceGroup())
               .addColumn(AnomaliesDataTableSchema.azureMeterCategory, anomaly.getAzureMeterCategory())
               .addColumn(AnomaliesDataTableSchema.awsAccount, anomaly.getAwsAccount())
               .addColumn(AnomaliesDataTableSchema.awsInstanceType, anomaly.getAwsInstanceType())
               .addColumn(AnomaliesDataTableSchema.awsService, anomaly.getAwsService())
               .addColumn(AnomaliesDataTableSchema.awsUsageType, anomaly.getAwsUsageType())
               .addColumn(AnomaliesDataTableSchema.anomalyScore, anomaly.getAnomalyScore())
               .addColumn(AnomaliesDataTableSchema.reportedBy, anomaly.getReportedBy())
               .addColumn(AnomaliesDataTableSchema.newEntity, ((Boolean) anomaly.isNewEntity()).toString())
               .validate()
               .toString()
        + " ON CONFLICT "
        + String.format("(%1$s,%2$s) ", AnomaliesDataTableSchema.id.getColumnNameSQL(),
            AnomaliesDataTableSchema.anomalyTime.getColumnNameSQL())
        + "DO UPDATE SET "
        + String.format("%s = %f , %s = %f ", AnomaliesDataTableSchema.actualCost.getColumnNameSQL(),
            anomaly.getActualCost(), AnomaliesDataTableSchema.expectedCost.getColumnNameSQL(),
            anomaly.getExpectedCost());
  }
}
