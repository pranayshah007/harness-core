/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.response;

public enum ElastigroupCommandTypeNG {
  ELASTIGROUP_SETUP,
  ELASTIGROUP_DEPLOY,
  ELASTIGROUP_SWAP_ROUTES,
  ELASTIGROUP_LIST_ELASTI_GROUPS,
  ELASTIGROUP_GET_ELASTI_GROUP_JSON,
  ELASTIGROUP_LIST_ELASTI_GROUP_INSTANCES,
  ELASTIGROUP_ALB_SHIFT_SETUP,
  ELASTIGROUP_ALB_SHIFT_DEPLOY,
  ELASTIGROUP_ALB_SHIFT_SWAP_ROUTES
}
