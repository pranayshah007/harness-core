/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static java.lang.String.format;

import io.harness.ccm.commons.beans.config.GcpConfig;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.annotation.Nullable;

public class BigQueryHelper {
  @Inject @Nullable @Named("gcpConfig") GcpConfig config;

  public static final String UNIFIED_TABLE = "unifiedTable";
  private static final String DATA_SET_NAME_TEMPLATE = "BillingReport_%s";
  private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";
  private static final String AWS_RAW_TABLE = "awscur*";
  private static final String GCP_RAW_TABLE = "gcp_billing_export*";
  private static final String AZURE_RAW_TABLE = "azureBilling_%s_%s";
  private static final String CE_INTERNAL_DATASET = "CE_INTERNAL";

  public String getCloudProviderTableName(String accountId, String tableName) {
    String projectId = config.getGcpProjectId();
    String dataSetId = getDataSetId(accountId);
    return format("%s.%s.%s", projectId, dataSetId, tableName);
  }

  public String getInformationSchemaViewForDataset(String accountId, String view) {
    String projectId = config.getGcpProjectId();
    String dataSetId = getDataSetId(accountId);
    return format("%s.%s.%s.%s", projectId, dataSetId, INFORMATION_SCHEMA, view);
  }

  public String getCEInternalDatasetTable(String view) {
    String projectId = config.getGcpProjectId();
    return format("%s.%s.%s", projectId, CE_INTERNAL_DATASET, view);
  }

  public static String getTableName(String cloudProvider) {
    switch (cloudProvider) {
      case "AWS":
        return AWS_RAW_TABLE;
      case "GCP":
        return GCP_RAW_TABLE;
      case "AZURE":
        return getAzureRawTable();
      default:
        throw new InvalidRequestException("Invalid Cloud Provider");
    }
  }

  private static String getAzureRawTable() {
    LocalDateTime localNow = LocalDateTime.now();
    String currentData = localNow.atZone(ZoneId.of("UTC")).toString();
    String[] dateElements = currentData.split("-");
    return format(AZURE_RAW_TABLE, dateElements[0], dateElements[1]);
  }

  public String getGcpProjectId() {
    return config.getGcpProjectId();
  }

  public static String getDataSetId(String accountId) {
    return String.format(DATA_SET_NAME_TEMPLATE, modifyStringToComplyRegex(accountId));
  }

  public static String modifyStringToComplyRegex(String accountInfo) {
    return accountInfo.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }
}
