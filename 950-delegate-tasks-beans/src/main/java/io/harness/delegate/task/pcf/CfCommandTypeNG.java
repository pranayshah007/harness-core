/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.pcf;

public enum CfCommandTypeNG {
  APP_RESIZE,
  ROLLBACK,
  SWAP_ROUTES,
  TANZU_COMMAND,
  TAS_BASIC_SETUP,
  TAS_BG_SETUP,
  SWAP_ROLLBACK,
  DATA_FETCH,
  TAS_ROLLING_DEPLOY,
  TAS_ROLLING_ROLLBACK,
  ROUTE_MAPPING
}
