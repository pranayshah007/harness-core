/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import software.wings.utils.Utils;

import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
public enum K8sTaskType {
  DEPLOYMENT_ROLLING,
  DEPLOYMENT_ROLLING_ROLLBACK,
  SCALE,
  CANARY_DEPLOY,
  BLUE_GREEN_DEPLOY,
  INSTANCE_SYNC,
  DELETE,
  TRAFFIC_SPLIT,
  APPLY,
  VERSION,
  SWAP_SERVICE_SELECTORS,
  CANARY_DELETE,

  // RANCHER
  RANCHER_RESOLVE_CLUSTERS;

  @JsonValue
  public K8sTaskType fromString(String taskType) {
    return Utils.getEnumFromString(K8sTaskType.class, taskType);
  }
}
