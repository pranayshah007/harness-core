/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.anomaly.QLAnomalyFeedback;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSpec;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "AnomalyEntityKeys")
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class AnomalyEntity {
  String id;
  String accountId;

  Instant anomalyTime;

  Double actualCost;
  Double expectedCost;
  Double anomalyScore;

  QLAnomalyFeedback feedback;

  AnomalyDetectionModel reportedBy;

  String note;
  TimeGranularity timeGranularity;

  String region;
  String cloudProvider;

  String clusterId;
  String clusterName;
  String workloadName;
  String workloadType;
  String namespace;

  String gcpProject;
  String gcpSKUId;
  String gcpSKUDescription;
  String gcpProduct;

  String awsAccount;
  String awsService;
  String awsInstanceType;
  String awsUsageType;

  String azureSubscription;
  String azureResourceGroup;
  String azureMeterCategory;

  boolean slackDailyNotification;
  boolean slackInstantNotification;
  boolean slackWeeklyNotification;
  boolean newEntity;

  public EntityType getEntityType() {
    if (workloadName != null) {
      return EntityType.WORKLOAD;
    }
    if (namespace != null) {
      return EntityType.NAMESPACE;
    }
    if (clusterId != null) {
      return EntityType.CLUSTER;
    }
    if (gcpSKUId != null) {
      return EntityType.GCP_SKU_ID;
    }
    if (gcpProduct != null) {
      return EntityType.GCP_PRODUCT;
    }
    if (gcpProject != null) {
      return EntityType.GCP_PROJECT;
    }
    if (awsInstanceType != null) {
      return EntityType.AWS_INSTANCE_TYPE;
    }
    if (awsUsageType != null) {
      return EntityType.AWS_USAGE_TYPE;
    }
    if (awsService != null) {
      return EntityType.AWS_SERVICE;
    }
    if (awsAccount != null) {
      return EntityType.AWS_ACCOUNT;
    }
    if (azureSubscription != null) {
      return EntityType.AZURE_SUBSCRIPTION;
    }
    if (azureResourceGroup != null) {
      return EntityType.AZURE_RESOURCE_GROUP;
    }
    if (azureMeterCategory != null) {
      return EntityType.AZURE_METER_CATEGORY;
    }

    return null;
  }

  public String getEntityId() {
    if (workloadName != null) {
      return workloadName;
    }
    if (namespace != null) {
      return namespace;
    }
    if (clusterId != null) {
      return clusterId;
    }
    if (gcpSKUId != null) {
      return gcpSKUId;
    }
    if (gcpProduct != null) {
      return gcpProduct;
    }
    if (gcpProject != null) {
      return gcpProject;
    }
    if (awsInstanceType != null) {
      return awsInstanceType;
    }
    if (awsUsageType != null) {
      return awsUsageType;
    }
    if (awsService != null) {
      return awsService;
    }
    if (awsAccount != null) {
      return awsAccount;
    }
    if (azureSubscription != null) {
      return azureSubscription;
    }
    if (azureResourceGroup != null) {
      return azureResourceGroup;
    }
    if (azureMeterCategory != null) {
      return azureMeterCategory;
    }
    return null;
  }
  public static class AnomaliesDataTableSchema {
    enum DataType { STRING, INTEGER, TIMESTAMP, DOUBLE, BOOLEAN }

    public static List<fields> getFields() {
      return Arrays.asList(fields.class.getEnumConstants());
    }

    public enum fields {
      ID("id", DataType.STRING),
      ACCOUNT_ID("accountid", DataType.STRING),
      ACTUAL_COST("actualcost", DataType.DOUBLE),
      EXPECTED_COST("expectedcost", DataType.DOUBLE),
      REGION("region", DataType.STRING),
      NOTE("note", DataType.STRING),
      FEED_BACK("feedback", DataType.STRING),
      ANOMALY_TIME("anomalytime", DataType.TIMESTAMP),
      TIME_GRANULARITY("timegranularity", DataType.STRING),
      CLUSTER_ID("clusterid", DataType.STRING),
      CLUSTER_NAME("clustername", DataType.STRING),
      NAMESPACE("namespace", DataType.STRING),
      WORKLOAD_TYPE("workloadtype", DataType.STRING),
      WORKLOAD_NAME("workloadname", DataType.STRING),
      GCP_PROJECT("gcpproject", DataType.STRING),
      GCP_PRODUCT("gcpproduct", DataType.STRING),
      GCP_SKU_ID("gcpskuid", DataType.STRING),
      GCP_SKU_DESCRIPTION("gcpskudescription", DataType.STRING),
      AWS_ACCOUNT("awsaccount", DataType.STRING),
      AWS_SERVICE("awsservice", DataType.STRING),
      AWS_USAGE_TYPE("awsusagetype", DataType.STRING),
      AWS_INSTANCE_TYPE("awsinstancetype", DataType.STRING),
      AZURE_SUBSCRIPTION("azuresubscriptionguid", DataType.STRING),
      AZURE_RESOURCE_GROUP("azureresourcegroup", DataType.STRING),
      AZURE_METER_CATEGORY("azuremetercategory", DataType.STRING),
      ANOMALY_SCORE("anomalyScore", DataType.DOUBLE),
      REPORTED_BY("reportedby", DataType.STRING),
      SLACK_INSTANT_NOTIFICATION("slackInstantNotification", DataType.BOOLEAN),
      SLACK_DAILY_NOTIFICATION("slackDailyNotification", DataType.BOOLEAN),
      SLACK_WEEKLY_NOTIFICATION("slackWeeklyNotification", DataType.BOOLEAN),
      NEW_ENTITY("newentity", DataType.BOOLEAN);

      private DataType dataType;
      private String fieldName;

      fields(String fieldName, DataType dataType) {
        this.fieldName = fieldName;
        this.dataType = dataType;
      }

      public DataType getDataType() {
        return dataType;
      }

      public String getFieldName() {
        return fieldName;
      }
    }

    public static final DbSpec spec;
    public static final DbSchema schema;
    public static final DbTable table;

    public static final DbColumn id;
    public static final DbColumn accountId;

    public static final DbColumn actualCost;
    public static final DbColumn expectedCost;

    public static final DbColumn anomalyTime;
    public static final DbColumn timeGranularity;

    public static final DbColumn note;
    public static final DbColumn feedBack;

    public static final DbColumn clusterId;
    public static final DbColumn clusterName;

    public static final DbColumn workloadName;
    public static final DbColumn workloadType;

    public static final DbColumn namespace;

    public static final DbColumn region;

    public static final DbColumn gcpProject;
    public static final DbColumn gcpProduct;
    public static final DbColumn gcpSkuId;
    public static final DbColumn gcpSkuDescription;

    public static final DbColumn awsAccount;
    public static final DbColumn awsService;
    public static final DbColumn awsUsageType;
    public static final DbColumn awsInstanceType;

    public static final DbColumn azureSubscription;
    public static final DbColumn azureResourceGroup;
    public static final DbColumn azureMeterCategory;

    public static final DbColumn anomalyScore;
    public static final DbColumn reportedBy;

    public static final DbColumn slackInstantNotification;
    public static final DbColumn slackDailyNotification;
    public static final DbColumn slackWeeklyNotification;

    public static final DbColumn newEntity;

    static {
      spec = new DbSpec();
      schema = spec.addDefaultSchema();
      table = schema.addTable("anomalies");

      id = table.addColumn("id");
      accountId = table.addColumn("accountid");

      actualCost = table.addColumn("actualcost");
      expectedCost = table.addColumn("expectedcost");

      anomalyTime = table.addColumn("anomalytime");
      timeGranularity = table.addColumn("timegranularity");

      note = table.addColumn("note");
      feedBack = table.addColumn("feedback");
      region = table.addColumn("region");

      clusterId = table.addColumn("clusterid");
      clusterName = table.addColumn("clustername");
      namespace = table.addColumn("namespace");
      workloadType = table.addColumn("workloadtype");
      workloadName = table.addColumn("workloadname");

      gcpProject = table.addColumn("gcpproject");
      gcpProduct = table.addColumn("gcpproduct");
      gcpSkuId = table.addColumn("gcpskuid");
      gcpSkuDescription = table.addColumn("gcpskudescription");

      awsAccount = table.addColumn("awsaccount");
      awsService = table.addColumn("awsservice");
      awsUsageType = table.addColumn("awsusagetype");
      awsInstanceType = table.addColumn("awsinstancetype");

      azureSubscription = table.addColumn("azuresubscriptionguid");
      azureResourceGroup = table.addColumn("azureresourcegroup");
      azureMeterCategory = table.addColumn("azuremetercategory");

      anomalyScore = table.addColumn("anomalyscore");
      reportedBy = table.addColumn("reportedby");

      slackInstantNotification = table.addColumn("slackInstantNotification");
      slackDailyNotification = table.addColumn("slackDailyNotification");
      slackWeeklyNotification = table.addColumn("slackWeeklyNotification");

      newEntity = table.addColumn("newentity");
    }
  }
}
