/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing.preaggregated;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class PreAggregateConstants {
  public static final String nullStringValueConstant = "Others";
  public static final String countStringValueConstant = "count";
  public static final String totalStringValueConstant = "Total";
  public static final String entityConstantRegion = "region";
  public static final String entityConstantAwsLinkedAccount = "awsUsageaccountid";
  public static final String entityConstantAwsUsageType = "awsUsagetype";
  public static final String entityConstantAwsInstanceType = "awsInstancetype";
  public static final String entityConstantAwsService = "awsServicecode";
  public static final String entityConstantAwsBlendedCost = "sum_blendedCost";
  public static final String entityConstantAwsUnBlendedCost = "sum_unblendedCost";

  public static final String entityConstantRawTableAwsLinkedAccount = "usageaccountid";
  public static final String entityConstantRawTableAwsUsageType = "usagetype";
  public static final String entityConstantRawTableAwsInstanceType = "instancetype";
  public static final String entityConstantRawTableAwsService = "productname";
  public static final String entityConstantRawTableAwsBlendedCost = "sum_blendedcost";
  public static final String entityConstantRawTableAwsUnBlendedCost = "sum_unblendedcost";
  public static final String entityConstantAwsTagKey = "tags_key";
  public static final String entityConstantAwsTagValue = "tags_value";

  public static final String minPreAggStartTimeConstant = "min_startTime";
  public static final String maxPreAggStartTimeConstant = "max_startTime";
  public static final String startTimeTruncatedConstant = "start_time_trunc";

  public static final String entityConstantGcpBillingAccount = "gcpBillingAccountId";
  public static final String entityConstantGcpProjectId = "gcpProjectId";
  public static final String entityConstantGcpProduct = "gcpProduct";
  public static final String entityConstantGcpSkuId = "gcpSkuId";
  public static final String entityConstantGcpSku = "gcpSkuDescription";
  public static final String entityConstantGcpCost = "sum_cost";
  public static final String entityConstantGcpDiscount = "sum_discount";
  public static final String entityConstantRawTableGcpBillingAccount = "billing_account_id";
  public static final String entityConstantRawTableGcpProjectId = "project_id";
  public static final String entityConstantRawTableGcpProduct = "service_description";
  public static final String entityConstantRawTableGcpSkuId = "sku_id";
  public static final String entityConstantRawTableGcpSku = "sku_description";
  public static final String entityConstantRawTableGcpRegion = "location_region";
  public static final String entityConstantGcpLabelKey = "labels_key";
  public static final String entityConstantGcpLabelValue = "labels_value";
  public static final String entityCloudProviderConst = "cloudProvider";

  public static final String entityConstantNoRegion = "No Region";
  public static final String entityConstantAwsNoLinkedAccount = "No Linked Account";
  public static final String entityConstantAwsNoUsageType = "No Usage Type";
  public static final String entityConstantAwsNoInstanceType = "No Instance Type";
  public static final String entityConstantAwsNoService = "No Service";
  public static final String entityConstantGcpNoProjectId = "No Project";
  public static final String entityConstantGcpNoProduct = "No Product";
  public static final String entityConstantGcpNoSkuId = "No SkuId";
  public static final String entityConstantGcpNoSku = "No Sku";
  public static final String entityNoCloudProviderConst = "No Cloud Provider";
  public static final String entityNoLabelConst = "Label not present";
  public static final String entityNoTagConst = "Tag not present";

  public static final String azureSubscriptionGuid = "azureSubscriptionGuid";
  public static final String azureResourceGroup = "azureResourceGroup";
  public static final String azureMeterCategory = "azureMeterCategory";
}
