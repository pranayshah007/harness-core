/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ClusterCostDetails", description = "Details of cluster cost")
public class ClusterCostDetails {
  Double totalCost;
  Double idleCost;
  Double unallocatedCost;
  String clusterType;
  String cluster;
  String clusterId;
  CCMK8sEntity k8s;
  CCMEcsEntity ecs;
}
