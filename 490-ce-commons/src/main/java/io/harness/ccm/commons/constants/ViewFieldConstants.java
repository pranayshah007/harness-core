/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.constants;

public interface ViewFieldConstants {
  String NONE_FIELD = "None";
  String AWS_ACCOUNT_FIELD = "Account";

  String CLUSTER_NAME_FIELD_ID = "clusterName";
  String CLUSTER_TYPE_FIELD_ID = "clusterType";
  String NAMESPACE_FIELD_ID = "namespace";
  String WORKLOAD_NAME_FIELD_ID = "workloadName";
  String INSTANCE_NAME_FIELD_ID = "instanceName";
  String STORAGE_FIELD_ID = "storage";
  String APP_NAME_FIELD_ID = "appName";
  String ENV_NAME_FIELD_ID = "envName";
  String SERVICE_NAME_FIELD_ID = "serviceName";
  String CLOUD_PROVIDER_FIELD_ID = "cloudProvider";
  String CLOUD_SERVICE_NAME_FIELD_ID = "cloudServiceName";
  String TASK_FIELD_ID = "taskId";
  String LAUNCH_TYPE_FIELD_ID = "launchType";

  String AWS_SERVICE_FIELD_ID = "awsServicecode";
  String AWS_ACCOUNT_FIELD_ID = "awsUsageaccountid";
  String AWS_INSTANCE_TYPE_FIELD_ID = "awsInstancetype";
  String AWS_USAGE_TYPE_ID = "awsUsageType";

  String AZURE_SUBSCRIPTION_GUID = "azureSubscriptionGuid";
  String AZURE_RESOURCE_GROUP = "azureResourceGroup";
  String AZURE_METER_CATEGORY = "azureMeterCategory";

  String GCP_PRODUCT_FIELD_ID = "gcpProduct";
  String GCP_PROJECT_FIELD_ID = "gcpProjectId";
  String GCP_SKU_DESCRIPTION_FIELD_ID = "gcpSkuDescription";
  String GCP_INVOICE_MONTH_FIELD_ID = "gcpInvoiceMonth";

  String REGION_FIELD_ID = "region";
  String PRODUCT_FIELD_ID = "product";
  long THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION = 4;
}
