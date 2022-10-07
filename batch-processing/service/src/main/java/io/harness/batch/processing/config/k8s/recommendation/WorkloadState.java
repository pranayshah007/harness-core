/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Value;

@Value
class WorkloadState {
  K8sWorkloadRecommendation recommendation;
  Map<String, ContainerState> containerStateMap;

  WorkloadState(K8sWorkloadRecommendation recommendation) {
    this.recommendation = recommendation;
    this.containerStateMap =
        Optional.ofNullable(recommendation.getContainerCheckpoints())
            .orElseGet(HashMap::new)
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> ContainerState.fromCheckpoint(e.getValue())));
  }
}
