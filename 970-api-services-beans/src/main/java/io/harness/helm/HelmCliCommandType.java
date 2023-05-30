/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

public enum HelmCliCommandType {
  INSTALL,
  UPGRADE,
  ROLLBACK,
  RELEASE_HISTORY,
  DELETE_RELEASE,
  LIST_RELEASE,
  REPO_ADD,
  REPO_UPDATE,
  REPO_LIST,
  SEARCH_REPO,
  VERSION,
  REPO_ADD_CHART_MUSEUM,
  REPO_ADD_HTTP,
  FETCH,
  REPO_REMOVE,
  INIT,
  RENDER_CHART,
  RENDER_SPECIFIC_CHART_FILE,
  FETCH_ALL_VERSIONS,
  OCI_REGISTRY_LOGIN,
  GET_MANIFEST;
}
