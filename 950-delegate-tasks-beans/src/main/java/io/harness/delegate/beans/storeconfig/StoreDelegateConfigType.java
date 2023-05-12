/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

public enum StoreDelegateConfigType {
  GIT,
  HTTP_HELM,
  S3_HELM,
  GCS_HELM,
  ARTIFACTORY,
  OCI_HELM,
  HARNESS,
  CUSTOM_REMOTE,
  INLINE,
  AMAZON_S3,
  GIT_FETCHED
}
