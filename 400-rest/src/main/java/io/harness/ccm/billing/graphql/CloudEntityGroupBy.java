/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.billing.RawBillingTableSchema;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;

@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public enum CloudEntityGroupBy {
  projectId(PreAggregatedTableSchema.gcpProjectId, RawBillingTableSchema.gcpProjectId, null),
  billingAccountId(PreAggregatedTableSchema.gcpBillingAccountId, RawBillingTableSchema.gcpBillingAccountId, null),
  skuId(PreAggregatedTableSchema.gcpSkuId, RawBillingTableSchema.gcpSkuId, null),
  product(PreAggregatedTableSchema.gcpProduct, RawBillingTableSchema.gcpProduct, null),
  sku(PreAggregatedTableSchema.gcpSkuDescription, RawBillingTableSchema.gcpSkuDescription, null),
  region(PreAggregatedTableSchema.region, RawBillingTableSchema.region, RawBillingTableSchema.awsRegion),
  labelsKey(null, RawBillingTableSchema.labelsKey, null),
  labelsValue(null, RawBillingTableSchema.labelsValue, null),

  tagsKey(null, null, RawBillingTableSchema.tagsKey),
  tagsValue(null, null, RawBillingTableSchema.tagsValue),
  awsLinkedAccount(PreAggregatedTableSchema.awsUsageAccountId, null, RawBillingTableSchema.awsUsageAccountId),
  awsUsageType(PreAggregatedTableSchema.awsUsageType, null, RawBillingTableSchema.awsUsageType),
  awsInstanceType(PreAggregatedTableSchema.awsInstanceType, null, RawBillingTableSchema.awsInstanceType),
  awsService(PreAggregatedTableSchema.awsServiceCode, null, RawBillingTableSchema.awsServiceCode),
  azureSubscriptionGuid(
      PreAggregatedTableSchema.azureSubscriptionGuid, null, RawBillingTableSchema.azureSubscriptionGuid),
  azureResourceGroup(PreAggregatedTableSchema.azureResourceGroup, null, RawBillingTableSchema.azureResourceGroup),
  azureMeterCategory(PreAggregatedTableSchema.azureMeterCategory, null, RawBillingTableSchema.azureMeterCategory),

  projectNumber(PreAggregatedTableSchema.gcpProjectNumbers, null, null),
  cloudProvider(PreAggregatedTableSchema.cloudProvider, null, null);

  private DbColumn dbColumn;
  private DbColumn rawDbColumn;
  private DbColumn awsRawDbColumn;

  CloudEntityGroupBy(DbColumn dbColumn, DbColumn rawDbColumn, DbColumn awsRawDbColumn) {
    this.dbColumn = dbColumn;
    this.rawDbColumn = rawDbColumn;
    this.awsRawDbColumn = awsRawDbColumn;
  }

  public DbColumn getDbObject() {
    return dbColumn;
  }

  DbColumn getRawDbObject() {
    return rawDbColumn;
  }

  DbColumn getAwsRawDbObject() {
    return awsRawDbColumn;
  }
}
