/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema.defaultTableName;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSpec;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import lombok.Value;
import lombok.experimental.UtilityClass;

@Value
@UtilityClass
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class RawBillingTableSchema {
  public static final DbSpec spec;
  public static final DbSchema schema;
  public static final DbTable table;

  public static final DbColumn gcpBillingAccountId;
  public static final DbColumn gcpProject;
  public static final DbColumn gcpProjectId;
  public static final DbColumn gcpProduct;
  public static final DbColumn gcpSkuId;
  public static final DbColumn gcpSkuDescription;
  public static final DbColumn cost;
  public static final DbColumn discount;
  public static final DbColumn zone;
  public static final DbColumn region;
  public static final DbColumn startTime;
  public static final DbColumn endTime;
  public static final DbColumn labelsKey;
  public static final DbColumn labelsValue;
  public static final DbColumn labels;
  public static final DbColumn tags;
  public static final DbColumn tagsKey;
  public static final DbColumn tagsValue;
  public static final DbColumn awsBlendedRate;
  public static final DbColumn awsBlendedCost;
  public static final DbColumn awsUnBlendedRate;
  public static final DbColumn awsUnBlendedCost;
  public static final DbColumn awsServiceCode;
  public static final DbColumn awsAvailabilityZone;
  public static final DbColumn awsUsageAccountId;
  public static final DbColumn awsInstanceType;
  public static final DbColumn awsUsageType;
  public static final DbColumn awsRegion;
  public static final DbColumn awsStartTime;
  public static final DbColumn azureSubscriptionGuid;
  public static final DbColumn azureResourceGroup;
  public static final DbColumn azureMeterCategory;

  static {
    spec = new DbSpec();
    schema = spec.addDefaultSchema();
    table = schema.addTable("`" + defaultTableName + "`");

    cost = table.addColumn("cost");
    region = table.addColumn("location.region");
    zone = table.addColumn("location.zone");
    startTime = table.addColumn("usage_start_time", "timestamp", null);
    endTime = table.addColumn("usage_end_time", "timestamp", null);
    gcpBillingAccountId = table.addColumn("billing_account_id");
    gcpProjectId = table.addColumn("project.id");
    gcpProject = table.addColumn("project.name");
    gcpProduct = table.addColumn("service.description");
    gcpSkuId = table.addColumn("sku.id");
    gcpSkuDescription = table.addColumn("sku.description");
    discount = table.addColumn("credits.amount");
    labelsKey = table.addColumn("labels.key");
    labelsValue = table.addColumn("labels.value");

    awsRegion = table.addColumn("region");
    awsStartTime = table.addColumn("usagestartdate");
    tagsKey = table.addColumn("tags.key");
    tagsValue = table.addColumn("tags.value");

    awsBlendedRate = table.addColumn("blendedrate");
    awsBlendedCost = table.addColumn("blendedcost");
    awsUnBlendedRate = table.addColumn("unblendedrate");
    awsUnBlendedCost = table.addColumn("unblendedcost");
    awsServiceCode = table.addColumn("productname");
    awsAvailabilityZone = table.addColumn("availabilityzone");
    awsUsageAccountId = table.addColumn("usageaccountid");
    awsInstanceType = table.addColumn("instancetype");
    awsUsageType = table.addColumn("usagetype");

    azureSubscriptionGuid = table.addColumn("azureSubscriptionGuid");
    azureResourceGroup = table.addColumn("azureResourceGroup");
    azureMeterCategory = table.addColumn("azureMeterCategory");

    labels = table.addColumn("labels");
    tags = table.addColumn("tags");
  }
}
