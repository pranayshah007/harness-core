/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CE)
public class ClusterTableKeys {
  public static final String CLUSTER_TABLE = "clusterData";
  public static final String CLUSTER_TABLE_HOURLY = "clusterDataHourly";
  public static final String CLUSTER_TABLE_AGGREGRATED = "clusterDataAggregated";
  public static final String CLUSTER_TABLE_HOURLY_AGGREGRATED = "clusterDataHourlyAggregated";
  public static final String CLUSTER_TABLE_HOURLY_AGGREGRATED_CH = "ccm.clusterDataHourlyAggregated";
  public static final String CLUSTER_TABLE_AGGREGRATED_CH = "ccm.clusterDataAggregated";
  public static final String START_TIME = "starttime";
  public static final String END_TIME = "endtime";
  public static final String ACCOUNT_ID = "accountid";
  public static final String BILLING_ACCOUNT_ID = "billingaccountid";
  public static final String INSTANCE_ID = "instanceid";
  public static final String INSTANCE_TYPE = "instancetype";
  public static final String INSTANCE_NAME = "instancename";
  public static final String SERVICE_ID = "serviceid";
  public static final String SERVICE_NAME = "servicename";
  public static final String APP_ID = "appid";
  public static final String APP_NAME = "appname";
  public static final String CLOUD_PROVIDER_ID = "cloudproviderid";
  public static final String CLOUD_PROVIDER = "cloudprovider";
  public static final String ENV_ID = "envid";
  public static final String ENV_NAME = "envname";
  public static final String CLUSTER_ID = "clusterid";
  public static final String CLUSTER_NAME = "clustername";
  public static final String PARENT_INSTANCE_ID = "parentinstanceid";
  public static final String REGION = "region";
  public static final String LAUNCH_TYPE = "launchtype";
  public static final String CLUSTER_TYPE = "clustertype";
  public static final String WORKLOAD_NAME = "workloadname";
  public static final String WORKLOAD_TYPE = "workloadtype";
  public static final String BILLING_AMOUNT = "billingamount";
  public static final String COST = "cost";
  public static final String MARKUP_AMOUNT = "markupAmount";
  public static final String CPU_BILLING_AMOUNT = "cpubillingamount";
  public static final String MEMORY_BILLING_AMOUNT = "memorybillingamount";
  public static final String USAGE_DURATION_SECONDS = "usagedurationseconds";
  public static final String CPU_UNIT_SECONDS = "cpuunitseconds";
  public static final String MEMORY_MB_SCONDS = "memoryMbSeconds";
  public static final String CLOUD_SERVICE_NAME = "cloudServiceName";
  public static final String TASK_ID = "taskId";
  public static final String NAMESPACE = "namespace";
  public static final String IDLE_COST = "idlecost";
  public static final String CPU_IDLE_COST = "cpuidlecost";
  public static final String MEMORY_IDLE_COST = "memoryidlecost";
  public static final String MAX_CPU_UTILIZATION = "maxCpuUtilization";
  public static final String MAX_MEMORY_UTILIZATION = "maxMemoryUtilization";
  public static final String AVG_CPU_UTILIZATION = "avgCpuUtilization";
  public static final String AVG_MEMORY_UTILIZATION = "avgMemoryUtilization";
  public static final String ACTUAL_IDLE_COST = "actualidlecost";
  public static final String CPU_ACTUAL_IDLE_COST = "cpuActualIdleCost";
  public static final String MEMORY_ACTUAL_IDLE_COST = "memoryActualIdleCost";
  public static final String UNALLOCATED_COST = "unallocatedcost";
  public static final String SYSTEM_COST = "systemCost";
  public static final String NETWORK_COST = "networkCost";
  public static final String MAX_CPU_UTILIZATION_VALUE = "maxcpuutilizationvalue";
  public static final String MAX_MEMORY_UTILIZATION_VALUE = "maxmemoryutilizationvalue";
  public static final String AVG_CPU_UTILIZATION_VALUE = "avgcpuutilizationvalue";
  public static final String AVG_MEMORY_UTILIZATION_VALUE = "avgmemoryutilizationvalue";
  public static final String CPU_REQUEST = "cpurequest";
  public static final String MEMORY_REQUEST = "memoryrequest";
  public static final String CPU_LIMIT = "cpulimit";
  public static final String MEMORY_LIMIT = "memorylimit";
  public static final String STORAGE_COST = "storagecost";
  public static final String STORAGE_ACTUAL_IDLE_COST = "storageactualidlecost";
  public static final String STORAGE_UTILIZATION_VALUE = "storageUtilizationValue";
  public static final String STORAGE_REQUEST = "storageRequest";
  public static final String STORAGE_UNALLOCATED_COST = "storageunallocatedcost";
  public static final String MEMORY_UNALLOCATED_COST = "memoryunallocatedcost";
  public static final String CPU_UNALLOCATED_COST = "cpuunallocatedcost";
  public static final String EFFECTIVE_CPU_REQUEST = "cpurequest*usagedurationseconds";
  public static final String EFFECTIVE_MEMORY_REQUEST = "memoryrequest*usagedurationseconds";
  public static final String EFFECTIVE_CPU_LIMIT = "cpulimit*usagedurationseconds";
  public static final String EFFECTIVE_MEMORY_LIMIT = "memorylimit*usagedurationseconds";
  public static final String EFFECTIVE_CPU_UTILIZATION_VALUE = "avgcpuutilizationvalue*usagedurationseconds";
  public static final String EFFECTIVE_MEMORY_UTILIZATION_VALUE = "avgmemoryutilizationvalue*usagedurationseconds";
  public static final String MARKUP_AMOUNT_AGGREGATION = "cost*(mspMarkupMultiplier - 1)";
  public static final String COUNT = "totalCount";
  public static final String COUNT_INNER = "totalCountInner";
  public static final String PRICING_SOURCE = "pricingsource";
  public static final String TIME_GRANULARITY = "time_granularity";

  // Some default constants
  public static final String DEFAULT_STRING_VALUE = "";
  public static final String DEFAULT_GRID_ENTRY_NAME = "Total";
  public static final double DEFAULT_DOUBLE_VALUE = -1d;
  public static final String ID_SEPARATOR = ":";
  public static final String DEFAULT_MARKUP_IDENTIFIER = "Markup";

  // Alias names for aggregation
  public static final String TIME_AGGREGATED_CPU_REQUEST = "timeAggregatedCpuRequest";
  public static final String TIME_AGGREGATED_MEMORY_REQUEST = "timeAggregatedMemoryRequest";
  public static final String TIME_AGGREGATED_CPU_LIMIT = "timeAggregatedCpuLimit";
  public static final String TIME_AGGREGATED_MEMORY_LIMIT = "timeAggregatedMemoryLimit";
  public static final String TIME_AGGREGATED_CPU_UTILIZATION_VALUE = "timeAggregatedCpuUtilizationValue";
  public static final String TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE = "timeAggregatedMemoryUtilizationValue";

  // Group by Names
  public static final String GROUP_BY_CLUSTER_NAME = "Cluster Name";
  public static final String GROUP_BY_CLUSTER_ID = "Cluster Id";
  public static final String GROUP_BY_NAMESPACE = "Namespace";
  public static final String GROUP_BY_NAMESPACE_ID = "Namespace Id";
  public static final String GROUP_BY_WORKLOAD_NAME = "Workload Name";
  public static final String GROUP_BY_WORKLOAD_ID = "Workload Id";
  public static final String GROUP_BY_WORKLOAD_TYPE = "Workload Type";
  public static final String GROUP_BY_ECS_SERVICE = "ECS Service";
  public static final String GROUP_BY_ECS_SERVICE_ID = "ECS Service Id";
  public static final String GROUP_BY_ECS_LAUNCH_TYPE = "ECS Launch Type";
  public static final String GROUP_BY_ECS_LAUNCH_TYPE_ID = "ECS Launch Type Id";
  public static final String GROUP_BY_ECS_TASK = "ECS Task";
  public static final String GROUP_BY_ECS_TASK_ID = "ECS Task Id";
  public static final String GROUP_BY_NODE = "Node";
  public static final String GROUP_BY_POD = "Pod";
  public static final String GROUP_BY_STORAGE = "Storage";
  public static final String GROUP_BY_APPLICATION = "Application";
  public static final String GROUP_BY_SERVICE = "Service";
  public static final String GROUP_BY_ENVIRONMENT = "Environment";
  public static final String GROUP_BY_CLOUD_PROVIDER = "Cloud Provider";
  public static final String GROUP_BY_PRODUCT = "Product";
  public static final String GROUP_BY_INSTANCE_ID = "Instance Id";
  public static final String GROUP_BY_INSTANCE_NAME = "Instance Name";
  public static final String GROUP_BY_INSTANCE_TYPE = "Instance Type";
  public static final String GROUP_BY_NONE = "None";
}
