/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.APP_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_BILLING_ENTITY;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_INSTANCE_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_LINE_ITEM_TYPE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_SERVICE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_USAGE_TYPE_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLOUD_PROVIDER_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLOUD_SERVICE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLUSTER_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLUSTER_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.ENV_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.GCP_PRODUCT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.GCP_PROJECT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.GCP_SKU_DESCRIPTION_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.INSTANCE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.LAUNCH_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.NAMESPACE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.NONE_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.REGION_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.SERVICE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.STORAGE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.TASK_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.graphql.QLCEViewField;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(CE)
public class ViewFieldUtils {
  public static final String UNIFIED_TABLE = "unifiedTable";
  public static final String CLUSTER_DATA_TABLE = "clusterData";
  public static final String CLUSTER_DATA_HOURLY_TABLE = "clusterDataHourly";
  public static final String CLUSTER_DATA_AGGREGATED_TABLE = "clusterDataAggregated";
  public static final String CLUSTER_DATA_HOURLY_AGGREGATED_TABLE = "clusterDataHourlyAggregated";
  public static final String COLUMN_MAPPING_KEY = "%s_%s";

  public static List<QLCEViewField> getAwsFields() {
    return ImmutableList.of(QLCEViewField.builder().fieldId(AWS_SERVICE_FIELD_ID).fieldName("Service").build(),
        QLCEViewField.builder().fieldId(AWS_ACCOUNT_FIELD_ID).fieldName("Account").build(),
        QLCEViewField.builder().fieldId(AWS_INSTANCE_TYPE_FIELD_ID).fieldName("Instance Type").build(),
        QLCEViewField.builder().fieldId(AWS_USAGE_TYPE_ID).fieldName("Usage Type").build(),
        QLCEViewField.builder().fieldId(AWS_BILLING_ENTITY).fieldName("Billing Entity").build(),
        QLCEViewField.builder().fieldId(AWS_LINE_ITEM_TYPE).fieldName("Line Item Type").build());
  }
  public static List<QLCEViewField> getGcpFields() {
    return ImmutableList.of(QLCEViewField.builder().fieldId(GCP_PRODUCT_FIELD_ID).fieldName("Product").build(),
        QLCEViewField.builder().fieldId(GCP_PROJECT_FIELD_ID).fieldName("Project").build(),
        QLCEViewField.builder().fieldId(GCP_SKU_DESCRIPTION_FIELD_ID).fieldName("SKUs").build());
  }
  public static List<QLCEViewField> getAzureFields() {
    return ImmutableList.of(
        QLCEViewField.builder().fieldId("azureSubscriptionGuid").fieldName("Subscription id").build(),
        QLCEViewField.builder().fieldId("azureMeterName").fieldName("Meter").build(),
        QLCEViewField.builder().fieldId("azureMeterCategory").fieldName("Meter category").build(),
        QLCEViewField.builder().fieldId("azureMeterSubcategory").fieldName("Meter subcategory").build(),
        QLCEViewField.builder().fieldId("azureMeterId").fieldName("Resource guid").build(),
        QLCEViewField.builder().fieldId("azureResourceGroup").fieldName("Resource group name").build(),
        QLCEViewField.builder().fieldId("azureResourceType").fieldName("Resource type").build(),
        QLCEViewField.builder().fieldId("azureResource").fieldName("Resource").build(),
        QLCEViewField.builder().fieldId("azureServiceName").fieldName("Service name").build(),
        QLCEViewField.builder().fieldId("azureServiceTier").fieldName("Service tier").build(),
        QLCEViewField.builder().fieldId("azureInstanceId").fieldName("Instance id").build());
  }

  public static List<QLCEViewField> getVariableAzureFields() {
    return ImmutableList.of(
        QLCEViewField.builder().fieldId("azureSubscriptionName").fieldName("Subscription name").build(),
        QLCEViewField.builder().fieldId("azurePublisherName").fieldName("Publisher name").build(),
        QLCEViewField.builder().fieldId("azurePublisherType").fieldName("Publisher type").build(),
        QLCEViewField.builder().fieldId("azureReservationId").fieldName("Reservation id").build(),
        QLCEViewField.builder().fieldId("azureReservationName").fieldName("Reservation name").build(),
        QLCEViewField.builder().fieldId("azureFrequency").fieldName("Frequency").build());
  }

  public static List<QLCEViewField> getClusterFields(boolean isClusterPerspective) {
    return isClusterPerspective ? getNgClusterFields() : getClusterFields();
  }

  public static List<QLCEViewField> getClusterFields() {
    return ImmutableList.of(QLCEViewField.builder().fieldId("clusterName").fieldName("Cluster Name").build(),
        QLCEViewField.builder().fieldId("clusterType").fieldName("Cluster Type").build(),
        QLCEViewField.builder().fieldId("namespace").fieldName("Namespace").build(),
        QLCEViewField.builder().fieldId("workloadName").fieldName("Workload").build(),
        QLCEViewField.builder().fieldId("appId").fieldName("Application").build(),
        QLCEViewField.builder().fieldId("envId").fieldName("Environment").build(),
        QLCEViewField.builder().fieldId("serviceId").fieldName("Service").build());
  }

  public static List<QLCEViewField> getNgClusterFields() {
    return ImmutableList.of(QLCEViewField.builder().fieldId(CLUSTER_NAME_FIELD_ID).fieldName("Cluster Name").build(),
        QLCEViewField.builder().fieldId(CLUSTER_TYPE_FIELD_ID).fieldName("Cluster Type").build(),
        QLCEViewField.builder().fieldId(NAMESPACE_FIELD_ID).fieldName("Namespace").build(),
        QLCEViewField.builder().fieldId(NAMESPACE_FIELD_ID).fieldName("Namespace Id").build(),
        QLCEViewField.builder().fieldId(WORKLOAD_NAME_FIELD_ID).fieldName("Workload").build(),
        QLCEViewField.builder().fieldId(WORKLOAD_NAME_FIELD_ID).fieldName("Workload Id").build(),
        QLCEViewField.builder().fieldId(INSTANCE_NAME_FIELD_ID).fieldName("Node").build(),
        QLCEViewField.builder().fieldId(STORAGE_FIELD_ID).fieldName("Storage").build(),
        QLCEViewField.builder().fieldId(APP_NAME_FIELD_ID).fieldName("Application").build(),
        QLCEViewField.builder().fieldId(ENV_NAME_FIELD_ID).fieldName("Environment").build(),
        QLCEViewField.builder().fieldId(SERVICE_NAME_FIELD_ID).fieldName("Service").build(),
        QLCEViewField.builder().fieldId(CLOUD_PROVIDER_FIELD_ID).fieldName("Cloud Provider").build(),
        QLCEViewField.builder().fieldId(CLOUD_SERVICE_NAME_FIELD_ID).fieldName("ECS Service").build(),
        QLCEViewField.builder().fieldId(CLOUD_SERVICE_NAME_FIELD_ID).fieldName("ECS Service Id").build(),
        QLCEViewField.builder().fieldId(TASK_FIELD_ID).fieldName("ECS Task").build(),
        QLCEViewField.builder().fieldId(TASK_FIELD_ID).fieldName("ECS Task Id").build(),
        QLCEViewField.builder().fieldId(LAUNCH_TYPE_FIELD_ID).fieldName("ECS Launch Type").build(),
        QLCEViewField.builder().fieldId(LAUNCH_TYPE_FIELD_ID).fieldName("ECS Launch Type Id").build());
  }

  public static List<QLCEViewField> getCommonFields() {
    return ImmutableList.of(QLCEViewField.builder().fieldId(REGION_FIELD_ID).fieldName("Region").build(),
        QLCEViewField.builder().fieldId("product").fieldName("Product").build(),
        QLCEViewField.builder().fieldId("cloudProvider").fieldName("Cloud Provider").build(),
        QLCEViewField.builder().fieldId("label").fieldName("Label").build(),
        QLCEViewField.builder().fieldId("none").fieldName(NONE_FIELD).build());
  }

  public static String getBusinessMappingUnallocatedCostDefaultName() {
    return "Cost categories default";
  }

  public static Map<String, String> getClickHouseColumnMapping() {
    Map<String, String> columnMapping = new HashMap<>();
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "starttime"), "startTime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "cost"), "cost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "gcpproduct"), "gcpProduct");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "gcpskuid"), "gcpSkuId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "gcpskudescription"), "gcpSkuDescription");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "gcpprojectid"), "gcpProjectId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "region"), "region");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "zone"), "zone");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "gcpbillingaccountid"), "gcpBillingAccountId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "cloudprovider"), "cloudProvider");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsblendedrate"), "awsBlendedRate");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsblendedcost"), "awsBlendedCost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsunblendedrate"), "awsUnblendedRate");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsunblendedcost"), "awsUnblendedCost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsservicecode"), "awsServicecode");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsavailabilityzone"), "awsAvailabilityZone");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsusageaccountid"), "awsUsageaccountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsinstancetype"), "awsInstancetype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsusagetype"), "awsUsagetype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "awsbillingentity"), "awsBillingEntity");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "discount"), "discount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "endtime"), "endtime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "accountid"), "accountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "instancetype"), "instancetype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "clusterid"), "clusterid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "clustername"), "clustername");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "appid"), "appid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "serviceid"), "serviceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "envid"), "envid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "cloudproviderid"), "cloudproviderid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "launchtype"), "launchtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "clustertype"), "clustertype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "workloadname"), "workloadname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "workloadtype"), "workloadtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "namespace"), "namespace");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "cloudservicename"), "cloudservicename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "taskid"), "taskid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "clustercloudprovider"), "clustercloudprovider");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "billingamount"), "billingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "cpubillingamount"), "cpubillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "memorybillingamount"), "memorybillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "idlecost"), "idlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "maxcpuutilization"), "maxcpuutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "avgcpuutilization"), "avgcpuutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "systemcost"), "systemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "actualidlecost"), "actualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "unallocatedcost"), "unallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "networkcost"), "networkcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "product"), "product");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuremetercategory"), "azureMeterCategory");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuremetersubcategory"), "azureMeterSubcategory");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuremeterid"), "azureMeterId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuremetername"), "azureMeterName");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureresourcetype"), "azureResourceType");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureservicetier"), "azureServiceTier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureinstanceid"), "azureInstanceId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureresourcegroup"), "azureResourceGroup");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuresubscriptionguid"), "azureSubscriptionGuid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureaccountname"), "azureAccountName");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurefrequency"), "azureFrequency");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurepublishertype"), "azurePublisherType");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurepublishername"), "azurePublisherName");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureservicename"), "azureServiceName");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuresubscriptionname"), "azureSubscriptionName");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurereservationid"), "azureReservationId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurereservationname"), "azureReservationName");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureresource"), "azureResource");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurevmproviderid"), "azureVMProviderId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azuretenantid"), "azureTenantId");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurebillingcurrency"), "azureBillingCurrency");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azurecustomername"), "azureCustomerName");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "azureresourcerate"), "azureResourceRate");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "orgidentifier"), "orgIdentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "projectidentifier"), "projectIdentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, UNIFIED_TABLE, "labels"), "labels");

    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "starttime"), "starttime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "endtime"), "endtime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "accountid"), "accountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "settingid"), "settingid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "instanceid"), "instanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "instancetype"), "instancetype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "billingaccountid"), "billingaccountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "clusterid"), "clusterid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "clustername"), "clustername");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "appid"), "appid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "serviceid"), "serviceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "envid"), "envid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "appname"), "appname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "envname"), "envname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "servicename"), "servicename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cloudproviderid"), "cloudproviderid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "parentinstanceid"), "parentinstanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "region"), "region");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "launchtype"), "launchtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "clustertype"), "clustertype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "workloadname"), "workloadname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "workloadtype"), "workloadtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "namespace"), "namespace");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cloudservicename"), "cloudservicename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "taskid"), "taskid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cloudprovider"), "cloudprovider");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "billingamount"), "billingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpubillingamount"), "cpubillingamount");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memorybillingamount"), "memorybillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "idlecost"), "idlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpuidlecost"), "cpuidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memoryidlecost"), "memoryidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "usagedurationseconds"), "usagedurationseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpuunitseconds"), "cpuunitseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memorymbseconds"), "memorymbseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "maxcpuutilization"), "maxcpuutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "maxmemoryutilization"), "maxmemoryutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "avgcpuutilization"), "avgcpuutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "avgmemoryutilization"), "avgmemoryutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "systemcost"), "systemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpusystemcost"), "cpusystemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memorysystemcost"), "memorysystemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "actualidlecost"), "actualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpuactualidlecost"), "cpuactualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memoryactualidlecost"), "memoryactualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "unallocatedcost"), "unallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpuunallocatedcost"), "cpuunallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memoryunallocatedcost"), "memoryunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "instancename"), "instancename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpurequest"), "cpurequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memoryrequest"), "memoryrequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "cpulimit"), "cpulimit");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "memorylimit"), "memorylimit");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "maxcpuutilizationvalue"), "maxcpuutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "maxmemoryutilizationvalue"),
        "maxmemoryutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "avgcpuutilizationvalue"), "avgcpuutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "avgmemoryutilizationvalue"),
        "avgmemoryutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "networkcost"), "networkcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "pricingsource"), "pricingsource");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "storageactualidlecost"), "storageactualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "storageunallocatedcost"), "storageunallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "storageutilizationvalue"), "storageutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "storagerequest"), "storagerequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "storagembseconds"), "storagembseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "storagecost"), "storagecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "maxstorageutilizationvalue"),
        "maxstorageutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "maxstoragerequest"), "maxstoragerequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "orgidentifier"), "orgidentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "projectidentifier"), "projectidentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_TABLE, "labels"), "labels");

    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "starttime"), "starttime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "endtime"), "endtime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "accountid"), "accountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "settingid"), "settingid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "instanceid"), "instanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "instancetype"), "instancetype");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "billingaccountid"), "billingaccountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "clusterid"), "clusterid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "clustername"), "clustername");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "appid"), "appid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "serviceid"), "serviceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "envid"), "envid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "appname"), "appname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "envname"), "envname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "servicename"), "servicename");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cloudproviderid"), "cloudproviderid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "parentinstanceid"), "parentinstanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "region"), "region");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "launchtype"), "launchtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "clustertype"), "clustertype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "workloadname"), "workloadname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "workloadtype"), "workloadtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "namespace"), "namespace");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cloudservicename"), "cloudservicename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "taskid"), "taskid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cloudprovider"), "cloudprovider");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "billingamount"), "billingamount");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpubillingamount"), "cpubillingamount");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memorybillingamount"), "memorybillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "idlecost"), "idlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpuidlecost"), "cpuidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memoryidlecost"), "memoryidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "usagedurationseconds"), "usagedurationseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpuunitseconds"), "cpuunitseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memorymbseconds"), "memorymbseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "maxcpuutilization"), "maxcpuutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "maxmemoryutilization"), "maxmemoryutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "avgcpuutilization"), "avgcpuutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "avgmemoryutilization"), "avgmemoryutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "systemcost"), "systemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpusystemcost"), "cpusystemcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memorysystemcost"), "memorysystemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "actualidlecost"), "actualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpuactualidlecost"), "cpuactualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memoryactualidlecost"), "memoryactualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "unallocatedcost"), "unallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpuunallocatedcost"), "cpuunallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memoryunallocatedcost"), "memoryunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "instancename"), "instancename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpurequest"), "cpurequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memoryrequest"), "memoryrequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "cpulimit"), "cpulimit");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "memorylimit"), "memorylimit");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "maxcpuutilizationvalue"),
        "maxcpuutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "maxmemoryutilizationvalue"),
        "maxmemoryutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "avgcpuutilizationvalue"),
        "avgcpuutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "avgmemoryutilizationvalue"),
        "avgmemoryutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "networkcost"), "networkcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "pricingsource"), "pricingsource");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "storageactualidlecost"), "storageactualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "storageunallocatedcost"),
        "storageunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "storageutilizationvalue"),
        "storageutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "storagerequest"), "storagerequest");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "storagembseconds"), "storagembseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "storagecost"), "storagecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "maxstorageutilizationvalue"),
        "maxstorageutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "maxstoragerequest"), "maxstoragerequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "orgidentifier"), "orgidentifier");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "projectidentifier"), "projectidentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_TABLE, "labels"), "labels");

    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "starttime"), "starttime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "endtime"), "endtime");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "accountid"), "accountid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "settingid"), "settingid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "instanceid"), "instanceid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "instancetype"), "instancetype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "billingaccountid"),
        "billingaccountid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "clusterid"), "clusterid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "clustername"), "clustername");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "appid"), "appid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "serviceid"), "serviceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "envid"), "envid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "appname"), "appname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "envname"), "envname");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "servicename"), "servicename");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cloudproviderid"), "cloudproviderid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "parentinstanceid"),
        "parentinstanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "region"), "region");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "launchtype"), "launchtype");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "clustertype"), "clustertype");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "workloadname"), "workloadname");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "workloadtype"), "workloadtype");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "namespace"), "namespace");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cloudservicename"),
        "cloudservicename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "taskid"), "taskid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cloudprovider"), "cloudprovider");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "billingamount"), "billingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpubillingamount"),
        "cpubillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memorybillingamount"),
        "memorybillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "idlecost"), "idlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpuidlecost"), "cpuidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memoryidlecost"), "memoryidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "usagedurationseconds"),
        "usagedurationseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpuunitseconds"), "cpuunitseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memorymbseconds"), "memorymbseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "maxcpuutilization"),
        "maxcpuutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "maxmemoryutilization"),
        "maxmemoryutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "avgcpuutilization"),
        "avgcpuutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "avgmemoryutilization"),
        "avgmemoryutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "systemcost"), "systemcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpusystemcost"), "cpusystemcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memorysystemcost"),
        "memorysystemcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "actualidlecost"), "actualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpuactualidlecost"),
        "cpuactualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memoryactualidlecost"),
        "memoryactualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "unallocatedcost"), "unallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpuunallocatedcost"),
        "cpuunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memoryunallocatedcost"),
        "memoryunallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "instancename"), "instancename");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpurequest"), "cpurequest");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memoryrequest"), "memoryrequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "cpulimit"), "cpulimit");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "memorylimit"), "memorylimit");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "maxcpuutilizationvalue"),
        "maxcpuutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "maxmemoryutilizationvalue"),
        "maxmemoryutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "avgcpuutilizationvalue"),
        "avgcpuutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "avgmemoryutilizationvalue"),
        "avgmemoryutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "networkcost"), "networkcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "pricingsource"), "pricingsource");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "storageactualidlecost"),
        "storageactualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "storageunallocatedcost"),
        "storageunallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "storageutilizationvalue"),
        "storageutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "storagerequest"), "storagerequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "storagembseconds"),
        "storagembseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "storagecost"), "storagecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "maxstorageutilizationvalue"),
        "maxstorageutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "maxstoragerequest"),
        "maxstoragerequest");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "orgidentifier"), "orgidentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "projectidentifier"),
        "projectidentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_HOURLY_AGGREGATED_TABLE, "labels"), "labels");

    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "starttime"), "starttime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "endtime"), "endtime");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "accountid"), "accountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "settingid"), "settingid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "instanceid"), "instanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "instancetype"), "instancetype");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "billingaccountid"), "billingaccountid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "clusterid"), "clusterid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "clustername"), "clustername");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "appid"), "appid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "serviceid"), "serviceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "envid"), "envid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "appname"), "appname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "envname"), "envname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "servicename"), "servicename");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cloudproviderid"), "cloudproviderid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "parentinstanceid"), "parentinstanceid");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "region"), "region");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "launchtype"), "launchtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "clustertype"), "clustertype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "workloadname"), "workloadname");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "workloadtype"), "workloadtype");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "namespace"), "namespace");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cloudservicename"), "cloudservicename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "taskid"), "taskid");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cloudprovider"), "cloudprovider");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "billingamount"), "billingamount");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpubillingamount"), "cpubillingamount");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memorybillingamount"), "memorybillingamount");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "idlecost"), "idlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpuidlecost"), "cpuidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memoryidlecost"), "memoryidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "usagedurationseconds"),
        "usagedurationseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpuunitseconds"), "cpuunitseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memorymbseconds"), "memorymbseconds");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "maxcpuutilization"), "maxcpuutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "maxmemoryutilization"),
        "maxmemoryutilization");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "avgcpuutilization"), "avgcpuutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "avgmemoryutilization"),
        "avgmemoryutilization");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "systemcost"), "systemcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpusystemcost"), "cpusystemcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memorysystemcost"), "memorysystemcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "actualidlecost"), "actualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpuactualidlecost"), "cpuactualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memoryactualidlecost"),
        "memoryactualidlecost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "unallocatedcost"), "unallocatedcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpuunallocatedcost"), "cpuunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memoryunallocatedcost"),
        "memoryunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "instancename"), "instancename");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpurequest"), "cpurequest");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memoryrequest"), "memoryrequest");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "cpulimit"), "cpulimit");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "memorylimit"), "memorylimit");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "maxcpuutilizationvalue"),
        "maxcpuutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "maxmemoryutilizationvalue"),
        "maxmemoryutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "avgcpuutilizationvalue"),
        "avgcpuutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "avgmemoryutilizationvalue"),
        "avgmemoryutilizationvalue");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "networkcost"), "networkcost");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "pricingsource"), "pricingsource");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "storageactualidlecost"),
        "storageactualidlecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "storageunallocatedcost"),
        "storageunallocatedcost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "storageutilizationvalue"),
        "storageutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "storagerequest"), "storagerequest");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "storagembseconds"), "storagembseconds");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "storagecost"), "storagecost");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "maxstorageutilizationvalue"),
        "maxstorageutilizationvalue");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "maxstoragerequest"), "maxstoragerequest");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "orgidentifier"), "orgidentifier");
    columnMapping.put(
        String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "projectidentifier"), "projectidentifier");
    columnMapping.put(String.format(COLUMN_MAPPING_KEY, CLUSTER_DATA_AGGREGATED_TABLE, "labels"), "labels");

    return columnMapping;
  }
}
