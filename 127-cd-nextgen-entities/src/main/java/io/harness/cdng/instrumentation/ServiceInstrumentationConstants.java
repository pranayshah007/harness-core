/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.instrumentation;

public final class ServiceInstrumentationConstants {
  public static String ACTIVE_SERVICES_COUNT_EVENT = "cd_active_services_count";
  public static String ACTIVE_SERVICES_COUNT = "count";
  public static String ACTIVE_SERVICES_ACCOUNT_ID = "accountId";
  public static String ACTIVE_SERVICES_ACCOUNT_NAME = "accountName";
  public static String ACTIVE_SERVICES_PROJECT_ID = "projectId";
  public static String ACTIVE_SERVICES_ORG_ID = "organizationId";
  public static String ACTIVE_SERVICES_PIPELINE_ID = "pipelineId";

  public static String SERVICE_USED_EVENT = "cd_service_used";
  public static String SERVICE_USED_EXECUTION_ID = "planExecutionId";
  public static String SERVICE_USED_SERVICE_ID = "serviceId";
  public static String SERVICE_USED_ACCOUNT_ID = "accountId";
  public static String SERVICE_USED_ACCOUNT_NAME = "accountName";
  public static String SERVICE_USED_PROJECT_ID = "projectId";
  public static String SERVICE_USED_ORG_ID = "organizationId";
  public static String SERVICE_USED_EVENT_PIPELINE_ID = "pipelineId";

  public static String SERVICE_INSTANCES_COUNT_EVENT = "cd_service_instances_count";
  public static String SERVICE_INSTANCES_COUNT = "count";
  public static String SERVICE_INSTANCES_ACCOUNT_ID = "accountId";
  public static String SERVICE_INSTANCES_ACCOUNT_NAME = "accountName";
  public static String SERVICE_INSTANCES_PROJECT_ID = "projectId";
  public static String SERVICE_INSTANCES_ORG_ID = "organizationId";
  public static String SERVICE_INSTANCES_PIPELINE_ID = "pipelineId";
}
