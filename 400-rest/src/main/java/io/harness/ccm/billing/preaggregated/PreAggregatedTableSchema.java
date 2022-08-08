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
public class PreAggregatedTableSchema {
  public static final String defaultTableName = "<Project>.<DataSet>.<TableName>";
  public static final DbSpec spec;
  public static final DbSchema schema;
  public static final DbTable table;

  public static final DbColumn gcpBillingAccountId;
  public static final DbColumn gcpProject;
  public static final DbColumn gcpProjectId;
  public static final DbColumn gcpProjectNumbers;
  public static final DbColumn gcpProduct;
  public static final DbColumn gcpSkuId;
  public static final DbColumn gcpSkuDescription;
  public static final DbColumn cost;
  public static final DbColumn discount;
  public static final DbColumn zone;
  public static final DbColumn cloudProvider;
  public static final DbColumn region;
  public static final DbColumn awsBlendedRate;
  public static final DbColumn awsBlendedCost;
  public static final DbColumn awsUnBlendedRate;
  public static final DbColumn awsUnBlendedCost;
  public static final DbColumn awsServiceCode;
  public static final DbColumn awsAvailabilityZone;
  public static final DbColumn awsUsageAccountId;
  public static final DbColumn azureSubscriptionGuid;
  public static final DbColumn azureResourceGroup;
  public static final DbColumn azureMeterCategory;
  public static final DbColumn awsInstanceType;
  public static final DbColumn awsUsageType;
  public static final DbColumn startTime;
  public static final DbColumn endTime;

  static {
    spec = new DbSpec();
    schema = spec.addDefaultSchema();
    table = schema.addTable("`" + defaultTableName + "`");

    zone = table.addColumn("zone");
    cost = table.addColumn("cost");
    discount = table.addColumn("discount");
    cloudProvider = table.addColumn("cloudProvider");
    region = table.addColumn("region");
    startTime = table.addColumn("startTime");
    endTime = table.addColumn("endTime");

    awsBlendedRate = table.addColumn("awsBlendedRate");
    awsBlendedCost = table.addColumn("awsBlendedCost");
    awsUnBlendedRate = table.addColumn("awsUnblendedRate");
    awsUnBlendedCost = table.addColumn("awsUnblendedCost");
    awsServiceCode = table.addColumn("awsServicecode");
    awsAvailabilityZone = table.addColumn("awsAvailabilityzone");
    awsUsageAccountId = table.addColumn("awsUsageaccountid");
    awsInstanceType = table.addColumn("awsInstancetype");
    awsUsageType = table.addColumn("awsUsagetype");

    gcpBillingAccountId = table.addColumn("gcpBillingAccountId");
    gcpProject = table.addColumn("gcpProject");
    gcpProjectId = table.addColumn("gcpProjectId");
    gcpProjectNumbers = table.addColumn("gcpProjectNumber");
    gcpProduct = table.addColumn("gcpProduct");
    gcpSkuId = table.addColumn("gcpSkuId");
    gcpSkuDescription = table.addColumn("gcpSkuDescription");

    azureSubscriptionGuid = table.addColumn("azureSubscriptionGuid");
    azureResourceGroup = table.addColumn("azureResourceGroup");
    azureMeterCategory = table.addColumn("azureMeterCategory");
  }
}
