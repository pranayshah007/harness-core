/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billingDataVerification.utils;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.ccm.billingDataVerification.dto.CCMBillingDataVerificationCost;
import io.harness.ccm.billingDataVerification.dto.CCMBillingDataVerificationKey;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.service.billingDataVerification.service.BillingDataVerificationSQLService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.services.costandusagereport.model.AWSCostAndUsageReportException;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.model.*;
import com.amazonaws.services.identitymanagement.model.AmazonIdentityManagementException;
import com.amazonaws.services.identitymanagement.model.EvaluationResult;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
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
import com.google.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class AwsBillingDataVerificationUtils {
  @Inject io.harness.aws.AwsClient awsClient;
  @Inject BigQueryHelper bigQueryHelper;
  @Inject BillingDataVerificationSQLService billingDataVerificationSQLService;

  public void fetchAndUpdateBillingDataForConnector(String accountId, ConnectorResponseDTO connector, String startDate,
      String endDate, AWSCredentialsProvider awsAssumedCredentialsProvider,
      Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData) {
    // startDate is inclusive, endDate is exclusive
    ConnectorInfoDTO connectorInfo = connector.getConnector();
    CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
    if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
      // for the connector, try calling the CostExplorer API in a try-catch: update results in the Map
      fetchBillingDataFromAWSCostExplorerAPI(
          accountId, connector, startDate, endDate, awsAssumedCredentialsProvider, billingData);

      // awsBilling_* group by usageaccountid, date --> aggregate columns: unblendedcost, blendedcost etc.
      fetchBillingDataFromAWSBillingTables(accountId, connector, startDate, endDate, billingData);

      // unifiedTable group by usageaccountid, date --> aggregate columns: cost, unblendedcost, blendedcost, amortised,
      // netAmortised etc.
      fetchAWSBillingDataFromUnifiedTable(accountId, connector, startDate, endDate, billingData);
    }
  }

  public CCMBillingDataVerificationCost mergeCostDTOs(
      CCMBillingDataVerificationCost c1, CCMBillingDataVerificationCost c2) {
    c1.setCostFromRawBillingTable(
        c2.getCostFromRawBillingTable() != null ? c2.getCostFromRawBillingTable() : c1.getCostFromRawBillingTable());
    c1.setCostFromUnifiedTable(
        c2.getCostFromUnifiedTable() != null ? c2.getCostFromUnifiedTable() : c1.getCostFromUnifiedTable());
    c1.setCostFromCloudProviderAPI(
        c2.getCostFromCloudProviderAPI() != null ? c2.getCostFromCloudProviderAPI() : c1.getCostFromCloudProviderAPI());
    return c1;
  }

  public void mergeResultsIntoBillingData(
      Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> queryResults,
      Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData) {
    queryResults.forEach(
        (key, value) -> { billingData.put(key, mergeCostDTOs(value, billingData.getOrDefault(key, value))); });
  }

  public void fetchAWSBillingDataFromUnifiedTable(String accountId, ConnectorResponseDTO connector, String startDate,
      String endDate, Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData) {
    Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> awsUnifiedTableResults =
        billingDataVerificationSQLService.fetchAWSCostsFromUnifiedTable(accountId, connector, startDate, endDate);
    mergeResultsIntoBillingData(awsUnifiedTableResults, billingData);
  }

  public void fetchBillingDataFromAWSBillingTables(String accountId, ConnectorResponseDTO connector, String startDate,
      String endDate, Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData) {
    Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> awsBillingResults =
        billingDataVerificationSQLService.fetchAWSCostsFromAWSBillingTables(accountId, connector, startDate, endDate);
    mergeResultsIntoBillingData(awsBillingResults, billingData);
  }

  public CCMBillingDataVerificationCost createNewCCMBillingDataVerificationCost(
      Double costFromRawBillingTable, Double costFromUnifiedTable, Double costFromCloudProviderAPI) {
    return CCMBillingDataVerificationCost.builder()
        .costFromRawBillingTable(costFromRawBillingTable)
        .costFromUnifiedTable(costFromUnifiedTable)
        .costFromCloudProviderAPI(costFromCloudProviderAPI)
        .build();
  }

  public void fetchBillingDataFromAWSCostExplorerAPI(String accountId, ConnectorResponseDTO connector, String startDate,
      String endDate, AWSCredentialsProvider awsAssumedCredentialsProvider,
      Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData) {
    final GetCostAndUsageRequest awsCERequest =
        new GetCostAndUsageRequest()
            .withTimePeriod(new DateInterval().withStart(startDate).withEnd(endDate))
            .withGranularity(Granularity.DAILY)
            .withMetrics(new String[] {"UnblendedCost", "BlendedCost", "AmortizedCost", "NetAmortizedCost"})
            .withGroupBy(new GroupDefinition().withType("DIMENSION").withKey("LINKED_ACCOUNT"));

    try {
      AWSCostExplorer ce =
          AWSCostExplorerClientBuilder.standard().withCredentials(awsAssumedCredentialsProvider).build();
      GetCostAndUsageResult result = ce.getCostAndUsage(awsCERequest);
      result.getResultsByTime().forEach(resultByTime -> {
        resultByTime.getGroups().forEach(group -> {
          String awsUsageAccountId = group.getKeys().get(0);

          // UnblendedCost
          CCMBillingDataVerificationKey unblendedCostKey =
              CCMBillingDataVerificationKey.builder()
                  .harnessAccountId(accountId)
                  .connectorId(connector.getConnector().getIdentifier())
                  .cloudProvider("AWS")
                  .cloudProviderAccountId(awsUsageAccountId)
                  .usageStartDate(LocalDate.parse(resultByTime.getTimePeriod().getStart()))
                  .usageEndDate(LocalDate.parse(resultByTime.getTimePeriod().getEnd()))
                  .costType("AWSUnblendedCost")
                  .build();
          billingData.put(unblendedCostKey,
              mergeCostDTOs(
                  billingData.getOrDefault(unblendedCostKey, createNewCCMBillingDataVerificationCost(null, null, null)),
                  createNewCCMBillingDataVerificationCost(
                      null, null, Double.parseDouble(group.getMetrics().get("UnblendedCost").getAmount()))));

          // BlendedCost
          CCMBillingDataVerificationKey blendedCostKey =
              CCMBillingDataVerificationKey.builder()
                  .harnessAccountId(accountId)
                  .connectorId(connector.getConnector().getIdentifier())
                  .cloudProvider("AWS")
                  .cloudProviderAccountId(awsUsageAccountId)
                  .usageStartDate(LocalDate.parse(resultByTime.getTimePeriod().getStart()))
                  .usageEndDate(LocalDate.parse(resultByTime.getTimePeriod().getEnd()))
                  .costType("AWSBlendedCost")
                  .build();
          billingData.put(blendedCostKey,
              mergeCostDTOs(
                  billingData.getOrDefault(blendedCostKey, createNewCCMBillingDataVerificationCost(null, null, null)),
                  createNewCCMBillingDataVerificationCost(
                      null, null, Double.parseDouble(group.getMetrics().get("BlendedCost").getAmount()))));

          // NetAmortizedCost
          CCMBillingDataVerificationKey netAmortizedCostKey =
              CCMBillingDataVerificationKey.builder()
                  .harnessAccountId(accountId)
                  .connectorId(connector.getConnector().getIdentifier())
                  .cloudProvider("AWS")
                  .cloudProviderAccountId(awsUsageAccountId)
                  .usageStartDate(LocalDate.parse(resultByTime.getTimePeriod().getStart()))
                  .usageEndDate(LocalDate.parse(resultByTime.getTimePeriod().getEnd()))
                  .costType("AWSNetAmortizedCost")
                  .build();
          billingData.put(netAmortizedCostKey,
              mergeCostDTOs(billingData.getOrDefault(
                                netAmortizedCostKey, createNewCCMBillingDataVerificationCost(null, null, null)),
                  createNewCCMBillingDataVerificationCost(
                      null, null, Double.parseDouble(group.getMetrics().get("NetAmortizedCost").getAmount()))));

          // AmortizedCost
          CCMBillingDataVerificationKey amortizedCostKey =
              CCMBillingDataVerificationKey.builder()
                  .harnessAccountId(accountId)
                  .connectorId(connector.getConnector().getIdentifier())
                  .cloudProvider("AWS")
                  .cloudProviderAccountId(awsUsageAccountId)
                  .usageStartDate(LocalDate.parse(resultByTime.getTimePeriod().getStart()))
                  .usageEndDate(LocalDate.parse(resultByTime.getTimePeriod().getEnd()))
                  .costType("AWSAmortizedCost")
                  .build();
          billingData.put(amortizedCostKey,
              mergeCostDTOs(
                  billingData.getOrDefault(amortizedCostKey, createNewCCMBillingDataVerificationCost(null, null, null)),
                  createNewCCMBillingDataVerificationCost(
                      null, null, Double.parseDouble(group.getMetrics().get("AmortizedCost").getAmount()))));
        });
      });
      ce.shutdown();
    } catch (final Exception e) {
      log.error("Exception while fetching billing-data from AWS Cost Explorer", e);
    }
  }
}
