/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.constants;

/*
  ViewFieldConstants but lower case, we use these in anomalies for case-insensitive matching
 */
public interface ViewFieldLowerCaseConstants {
  String NONE_FIELD = "none";
  String AWS_ACCOUNT_FIELD = "account";

  String CLUSTER_NAME_FIELD_ID = "clustername";
  String CLUSTER_TYPE_FIELD_ID = "clustertype";
  String NAMESPACE_FIELD_ID = "namespace";
  String WORKLOAD_NAME_FIELD_ID = "workloadname";
  String INSTANCE_NAME_FIELD_ID = "instancename";
  String STORAGE_FIELD_ID = "storage";
  String APP_NAME_FIELD_ID = "appname";
  String ENV_NAME_FIELD_ID = "envname";
  String SERVICE_NAME_FIELD_ID = "servicename";
  String CLOUD_PROVIDER_FIELD_ID = "cloudprovider";
  String CLOUD_SERVICE_NAME_FIELD_ID = "cloudservicename";
  String TASK_FIELD_ID = "taskid";
  String LAUNCH_TYPE_FIELD_ID = "launchtype";

  String AWS_SERVICE_FIELD_ID = "awsservicecode";
  String AWS_ACCOUNT_FIELD_ID = "awsusageaccountid";
  String AWS_INSTANCE_TYPE_FIELD_ID = "awsinstancetype";
  String AWS_USAGE_TYPE_ID = "awsusagetype";

  String AZURE_SUBSCRIPTION_GUID = "azuresubscriptionguid";
  String AZURE_RESOURCE_GROUP = "azureresourcegroup";
  String AZURE_METER_CATEGORY = "azuremetercategory";

  String GCP_PRODUCT_FIELD_ID = "gcpproduct";
  String GCP_PROJECT_FIELD_ID = "gcpprojectid";
  String GCP_SKU_DESCRIPTION_FIELD_ID = "gcpskudescription";

  String REGION_FIELD_ID = "region";
  String PRODUCT_FIELD_ID = "product";
}
