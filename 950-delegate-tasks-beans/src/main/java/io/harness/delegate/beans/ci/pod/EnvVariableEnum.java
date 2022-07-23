/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

public enum EnvVariableEnum {
  DOCKER_USERNAME,
  DOCKER_PASSWORD,
  DOCKER_REGISTRY,
  AWS_ACCESS_KEY,
  AWS_SECRET_KEY,
  AZURE_APPLICATION_ID,
  AZURE_TENANT_ID,
  AZURE_APP_SECRET,

  AWS_CROSS_ACCOUNT_ROLE_ARN,
  GCP_KEY,
  GCP_KEY_AS_FILE,
  ARTIFACTORY_USERNAME,
  ARTIFACTORY_PASSWORD,
  ARTIFACTORY_ENDPOINT,
  AWS_CROSS_ACCOUNT_EXTERNAL_ID,
}
