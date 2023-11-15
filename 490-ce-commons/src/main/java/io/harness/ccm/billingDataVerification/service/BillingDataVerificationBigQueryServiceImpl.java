/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.commons.utils.BigQueryHelper.*;

import io.harness.ccm.billingDataVerification.dto.CCMBillingDataVerificationCost;
import io.harness.ccm.billingDataVerification.dto.CCMBillingDataVerificationKey;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.service.billingDataVerification.service.BillingDataVerificationSQLService;
import io.harness.connector.ConnectorResponseDTO;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics.QueryStatistics;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@Slf4j
public class BillingDataVerificationBigQueryServiceImpl implements BillingDataVerificationSQLService {
  private static final String AWS_UNIFIED_TABLE_COST_VERIFICATION_QUERY_TEMPLATE =
      String.join(" ", "SELECT DATE_TRUNC(startTime, DAY) as day, awsUsageAccountId as cloudProviderAccountId, ",
          "sum(awsUnblendedCost) as unblendedCost, sum(awsBlendedCost) as blendedCost, ",
          "sum(awsAmortizedcost) as amortizedcost, sum(awsNetamortizedcost) as netamortizedcost ", "FROM `%s` ",
          "WHERE DATE_TRUNC(startTime, DAY) >= DATE('%s')", "AND DATE_TRUNC(startTime, DAY) < DATE('%s')",
          "AND cloudprovider='AWS'", "GROUP BY day, cloudProviderAccountId ;");

  private static final String AWS_BILLING_COST_VERIFICATION_QUERY_TEMPLATE =
      String.join(" ", "SELECT DATE_TRUNC(usagestartdate, DAY) as day, usageAccountId as cloudProviderAccountId, ",
          "sum(unblendedCost) as unblendedCost, sum(blendedCost) as blendedCost ", "FROM `%s` ",
          "WHERE DATE_TRUNC(usagestartdate, DAY) >= DATE('%s')", "AND DATE_TRUNC(usagestartdate, DAY) < DATE('%s')",
          "GROUP BY day, cloudProviderAccountId ;");

  private static final String DELETE_FROM_BILLING_DATA_VERIFICATION_TABLE_QUERY_TEMPLATE =
      String.join(" ", "DELETE FROM %s ", "WHERE harnessAccountId = '%s' AND ", "connectorId IN (%s) ; ");

  private static final String INSERT_INTO_BILLING_DATA_VERIFICATION_TABLE_QUERY_TEMPLATE = String.join(" ",
      "INSERT INTO %s ",
      "(harnessAccountId, connectorId, cloudProvider, cloudProviderAccountId, usageStartDate, usageEndDate, costType, costFromCloudProviderAPI, costFromRawBillingTable, costFromUnifiedTable) ",
      "VALUES %s ; ");

  @Inject BigQueryHelper bigQueryHelper;
  @Inject private io.harness.ccm.bigQuery.BigQueryService bigQueryService;

  @Override
  public Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> fetchAWSCostsFromAWSBillingTables(
      String accountId, ConnectorResponseDTO connector, String startDate, String endDate) {
    String awsBillingTableId = bigQueryHelper.getCloudProviderTableName(
        accountId, String.format(AWS_BILLING_RAW_TABLE, connector.getConnector().getIdentifier(), "*"));
    String selectQuery =
        String.format(AWS_BILLING_COST_VERIFICATION_QUERY_TEMPLATE, awsBillingTableId, startDate, endDate);
    TableResult result = executeQuery(selectQuery);
    Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> awsBillingResults = new HashMap<>();
    for (FieldValueList row : result.iterateAll()) {
      // unblended cost
      CCMBillingDataVerificationKey unblendedCostKey =
          CCMBillingDataVerificationKey.builder()
              .harnessAccountId(accountId)
              .connectorId(connector.getConnector().getIdentifier())
              .cloudProvider("AWS")
              .cloudProviderAccountId(row.get("cloudProviderAccountId").getStringValue())
              .usageStartDate(LocalDate.parse(row.get("day").getStringValue()))
              .usageEndDate(LocalDate.parse(row.get("day").getStringValue()).plusDays(1))
              .costType("AWSUnblendedCost")
              .build();
      awsBillingResults.put(unblendedCostKey,
          CCMBillingDataVerificationCost.builder()
              .costFromRawBillingTable(row.get("unblendedCost").getDoubleValue())
              .build());

      // blended cost
      CCMBillingDataVerificationKey blendedCostKey =
          CCMBillingDataVerificationKey.builder()
              .harnessAccountId(accountId)
              .connectorId(connector.getConnector().getIdentifier())
              .cloudProvider("AWS")
              .cloudProviderAccountId(row.get("cloudProviderAccountId").getStringValue())
              .usageStartDate(LocalDate.parse(row.get("day").getStringValue()))
              .usageEndDate(LocalDate.parse(row.get("day").getStringValue()).plusDays(1))
              .costType("AWSBlendedCost")
              .build();
      awsBillingResults.put(blendedCostKey,
          CCMBillingDataVerificationCost.builder()
              .costFromRawBillingTable(row.get("blendedCost").getDoubleValue())
              .build());
    }
    return awsBillingResults;
  }

  @Override
  public Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> fetchAWSCostsFromUnifiedTable(
      String accountId, ConnectorResponseDTO connector, String startDate, String endDate) {
    String unifiedTableId = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    String selectQuery =
        String.format(AWS_UNIFIED_TABLE_COST_VERIFICATION_QUERY_TEMPLATE, unifiedTableId, startDate, endDate);
    TableResult result = executeQuery(selectQuery);
    Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> awsUnifiedTableResults = new HashMap<>();
    for (FieldValueList row : result.iterateAll()) {
      // unblended cost
      CCMBillingDataVerificationKey unblendedCostKey =
          CCMBillingDataVerificationKey.builder()
              .harnessAccountId(accountId)
              .connectorId(connector.getConnector().getIdentifier())
              .cloudProvider("AWS")
              .cloudProviderAccountId(row.get("cloudProviderAccountId").getStringValue())
              .usageStartDate(LocalDate.parse(row.get("day").getStringValue()))
              .usageEndDate(LocalDate.parse(row.get("day").getStringValue()).plusDays(1))
              .costType("AWSUnblendedCost")
              .build();
      awsUnifiedTableResults.put(unblendedCostKey,
          CCMBillingDataVerificationCost.builder()
              .costFromUnifiedTable(row.get("unblendedCost").getDoubleValue())
              .build());

      // blended cost
      CCMBillingDataVerificationKey blendedCostKey =
          CCMBillingDataVerificationKey.builder()
              .harnessAccountId(accountId)
              .connectorId(connector.getConnector().getIdentifier())
              .cloudProvider("AWS")
              .cloudProviderAccountId(row.get("cloudProviderAccountId").getStringValue())
              .usageStartDate(LocalDate.parse(row.get("day").getStringValue()))
              .usageEndDate(LocalDate.parse(row.get("day").getStringValue()).plusDays(1))
              .costType("AWSBlendedCost")
              .build();
      awsUnifiedTableResults.put(blendedCostKey,
          CCMBillingDataVerificationCost.builder()
              .costFromUnifiedTable(row.get("blendedCost").getDoubleValue())
              .build());

      // amortizedcost cost
      CCMBillingDataVerificationKey amortizedCostKey =
          CCMBillingDataVerificationKey.builder()
              .harnessAccountId(accountId)
              .connectorId(connector.getConnector().getIdentifier())
              .cloudProvider("AWS")
              .cloudProviderAccountId(row.get("cloudProviderAccountId").getStringValue())
              .usageStartDate(LocalDate.parse(row.get("day").getStringValue()))
              .usageEndDate(LocalDate.parse(row.get("day").getStringValue()).plusDays(1))
              .costType("AWSAmortizedCost")
              .build();
      awsUnifiedTableResults.put(amortizedCostKey,
          CCMBillingDataVerificationCost.builder()
              .costFromUnifiedTable(row.get("amortizedcost").getDoubleValue())
              .build());

      // netamortizedcost cost
      CCMBillingDataVerificationKey netAmortizedCostKey =
          CCMBillingDataVerificationKey.builder()
              .harnessAccountId(accountId)
              .connectorId(connector.getConnector().getIdentifier())
              .cloudProvider("AWS")
              .cloudProviderAccountId(row.get("cloudProviderAccountId").getStringValue())
              .usageStartDate(LocalDate.parse(row.get("day").getStringValue()))
              .usageEndDate(LocalDate.parse(row.get("day").getStringValue()).plusDays(1))
              .costType("AWSNetAmortizedCost")
              .build();
      awsUnifiedTableResults.put(netAmortizedCostKey,
          CCMBillingDataVerificationCost.builder()
              .costFromUnifiedTable(row.get("netamortizedcost").getDoubleValue())
              .build());
    }
    return awsUnifiedTableResults;
  }

  @Override
  public void ingestAWSCostsIntoBillingDataVerificationTable(
      String accountId, Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData) {
    if (billingData.isEmpty())
      return;
    String ccmBillingDataVerificationTableId =
        bigQueryHelper.getCEInternalDatasetTable(CCM_BILLING_DATA_VERIFICATION_TABLE);

    List<String> connectorIds = billingData.keySet()
                                    .stream()
                                    .map(billingVerificationKey -> "'" + billingVerificationKey.getConnectorId() + "'")
                                    .collect(Collectors.toList());
    String deleteQuery = String.format(DELETE_FROM_BILLING_DATA_VERIFICATION_TABLE_QUERY_TEMPLATE,
        ccmBillingDataVerificationTableId, accountId, String.join(",", connectorIds));
    executeQuery(deleteQuery);

    List<String> rows = new ArrayList<>();
    for (var entry : billingData.entrySet()) {
      rows.add(String.format("('%s', ", accountId)
                   .concat(String.format("'%s', ", entry.getKey().getConnectorId()))
                   .concat(String.format("'%s', ", "AWS"))
                   .concat(String.format("'%s', ", entry.getKey().getCloudProviderAccountId()))
                   .concat(String.format("'%s', ", entry.getKey().getUsageStartDate()))
                   .concat(String.format("'%s', ", entry.getKey().getUsageEndDate()))
                   .concat(String.format("'%s', ", entry.getKey().getCostType()))

                   .concat(String.format("'%s', ", entry.getValue().getCostFromCloudProviderAPI()))
                   .concat(String.format("'%s', ", entry.getValue().getCostFromRawBillingTable()))
                   .concat(String.format("'%s')", entry.getValue().getCostFromUnifiedTable())));
    }
    String insertQuery = String.format(INSERT_INTO_BILLING_DATA_VERIFICATION_TABLE_QUERY_TEMPLATE,
        ccmBillingDataVerificationTableId, String.join(" ", rows));
    executeQuery(insertQuery);
  }

  private TableResult executeQuery(final String query) {
    final BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      log.info("Executing Query: {}", query);
      result = bigQuery.query(queryConfig);
    } catch (final InterruptedException e) {
      log.error("Failed to execute query: {}", queryConfig, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return result;
  }
}
