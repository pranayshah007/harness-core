/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s;

import io.harness.batch.processing.ccm.EnrichedEvent;
import io.harness.batch.processing.k8s.rcd.ResourceClaim;
import io.harness.batch.processing.k8s.rcd.ResourceClaimDiff;
import io.harness.batch.processing.k8s.rcd.ResourceClaimDiffCalculator;
import io.harness.perpetualtask.k8s.watch.K8sWatchEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WatchEventCostEstimator {
  private Map<String, ResourceClaimDiffCalculator> resourceClaimDiffCalculators;

  @Autowired
  public WatchEventCostEstimator(List<ResourceClaimDiffCalculator> resourceClaimDiffCalculators) {
    this.resourceClaimDiffCalculators = resourceClaimDiffCalculators.stream().collect(
        Collectors.toMap(ResourceClaimDiffCalculator::getKind, Function.identity()));
  }

  public EstimatedCostDiff estimateCostDiff(EnrichedEvent<K8sWatchEvent> eK8sWatchEvent) {
    K8sWatchEvent k8sWatchEvent = eK8sWatchEvent.getEvent();
    String kind = k8sWatchEvent.getResourceRef().getKind();
    String oldYaml = k8sWatchEvent.getOldResourceYaml();
    String newYaml = k8sWatchEvent.getNewResourceYaml();
    ResourceClaimDiff resourceClaimDiff = Optional.ofNullable(resourceClaimDiffCalculators.get(kind))
                                              .map(rcDiffCalc -> rcDiffCalc.computeResourceClaimDiff(oldYaml, newYaml))
                                              .orElseGet(() -> {
                                                log.warn("Unknown kind: {}", kind);
                                                return new ResourceClaimDiff(ResourceClaim.EMPTY, ResourceClaim.EMPTY);
                                              });
    return new EstimatedCostDiff(resourceToCost(resourceClaimDiff.getOldResourceClaim()),
        resourceToCost(resourceClaimDiff.getNewResourceClaim()));
  }

  private static BigDecimal resourceToCost(ResourceClaim resourceClaim) {
    return BigDecimal.valueOf(resourceClaim.getCpuNano())
        .divide(BigDecimal.valueOf(1_000_000_000L), RoundingMode.FLOOR)
        .multiply(getAvgCpuCostPerCpuCore())
        .add(BigDecimal.valueOf(resourceClaim.getMemBytes())
                 .divide(BigDecimal.valueOf(1024L * 1024 * 1024), RoundingMode.FLOOR)
                 .multiply(getAvgMemCostPerMemGb()));
  }

  public static BigDecimal resourceToCost(BigDecimal cpuCores, BigDecimal memoryBytes) {
    return cpuCores.multiply(getAvgCpuCostPerCpuCore())
        .add(memoryBytes.divide(BigDecimal.valueOf(1024L * 1024 * 1024), RoundingMode.FLOOR)
                 .multiply(getAvgMemCostPerMemGb()));
  }

  // The workload is spread across multiple nodes and each of these can have different unit cpu/memory costs.
  // We're using average here.

  // TODO: Need to check how to get the average cpu & memory cost for the workload. Hardcoded values for AWS used
  private static BigDecimal getAvgCpuCostPerCpuCore() {
    return BigDecimal.valueOf(0.0255);
  }

  private static BigDecimal getAvgMemCostPerMemGb() {
    return BigDecimal.valueOf(0.01275);
  }
}
