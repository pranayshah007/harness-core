/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.gcp.bigquery;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord.CEMetadataRecordBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CE)
public interface BigQueryHelperService {
  Map<String, VMInstanceBillingData> getAwsEC2BillingData(
      List<String> resourceId, Instant startTime, Instant endTime, String dataSetId, String accountId);

  Map<String, VMInstanceBillingData> getEKSFargateBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId);

  Map<String, VMInstanceBillingData> getAwsBillingData(
      Instant startTime, Instant endTime, String dataSetId, String accountId);

  void updateCloudProviderMetaData(String accountId, CEMetadataRecordBuilder ceMetadataRecordBuilder);

  Map<String, VMInstanceBillingData> getAzureVMBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId);

  Map<String, VMInstanceBillingData> getGcpVMBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId);

  void addCostCategoriesColumnInUnifiedTable(String tableName);

  void removeAllCostCategories(String tableName, String startTime, String endTime, CloudProvider cloudProvider,
      List<String> cloudProviderAccountIds);

  void insertCostCategories(String tableName, String costCategoriesStatement, String startTime, String endTime,
      CloudProvider cloudProvider, List<String> cloudProviderAccountIds) throws InterruptedException;

  void addCostCategory(String tableName, String costCategoriesStatement, String startTime, String endTime,
      CloudProvider cloudProvider, List<String> cloudProviderAccountIds) throws InterruptedException;
}
