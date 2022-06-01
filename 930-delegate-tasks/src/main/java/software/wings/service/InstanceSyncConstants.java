/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

public final class InstanceSyncConstants {
  private InstanceSyncConstants() {}

  public static final String HARNESS_ACCOUNT_ID = "accountId";
  public static final String HARNESS_APPLICATION_ID = "applicationId";
  public static final String HARNESS_ENV_ID = "environmentId";
  public static final String INFRASTRUCTURE_MAPPING_ID = "infrastructureMappingId";
  public static final String NAMESPACE = "namespace";
  public static final String RELEASE_NAME = "releaseName";
  public static final String CLUSTER_NAME = "clusterName";
  public static final String CONTAINER_SERVICE_NAME = "containerSvcName";
  public static final String CONTAINER_TYPE = "containerType";
  public static final String HOSTNAMES = "hostNames";
  public static final int TIMEOUT_SECONDS = 600;
  public static final int INTERVAL_MINUTES = 10;
  public static final int VALIDATION_TIMEOUT_MINUTES = 2;
}
