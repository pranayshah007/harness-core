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
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_INSTANCE_TYPE_FIELD_ID;
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
import java.util.List;

@OwnedBy(CE)
public class ViewFieldUtils {
  public static List<QLCEViewField> getAwsFields() {
    return ImmutableList.of(QLCEViewField.builder().fieldId(AWS_SERVICE_FIELD_ID).fieldName("Service").build(),
        QLCEViewField.builder().fieldId(AWS_ACCOUNT_FIELD_ID).fieldName("Account").build(),
        QLCEViewField.builder().fieldId(AWS_INSTANCE_TYPE_FIELD_ID).fieldName("Instance Type").build(),
        QLCEViewField.builder().fieldId(AWS_USAGE_TYPE_ID).fieldName("Usage Type").build());
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
}
